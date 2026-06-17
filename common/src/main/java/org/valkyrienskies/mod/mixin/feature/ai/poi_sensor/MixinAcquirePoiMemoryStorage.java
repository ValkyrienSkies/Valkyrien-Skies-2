package org.valkyrienskies.mod.mixin.feature.ai.poi_sensor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.pathfinding.PathPerFrameRegistry;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;
import org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder.PathAccessor;

// Hand AcquirePoi the actual shipyard pos of the chosen POI candidate so its
// getType()/take()/HOME-memory writes land on the right cell.
@Mixin(AcquirePoi.class)
public abstract class MixinAcquirePoiMemoryStorage {

    // method_46885: the inner trigger lambda that runs after a path to a POI is found
    // and calls poiManager.getType(pathTarget) / take() / set(HOME memory).
    @WrapOperation(
        method = "method_46885",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/Path;getTarget()Lnet/minecraft/core/BlockPos;"
        )
    )
    private static BlockPos vs$projectPathTargetToShipyard(
        final Path path,
        final Operation<BlockPos> original,
        @Local(argsOnly = true) final ServerLevel level
    ) {
        // InShip path: skip MixinPath's lossy shipyard→world reprojection; flooring
        // the round-trip lands AcquirePoi's getType lookup on a non-POI cell.
        final int n = path.getNodeCount();
        if (n > 0) {
            final PathfindingFrame frame = PathPerFrameRegistry.getFrameAtNodeIndex(path, n - 1);
            if (frame instanceof PathfindingFrame.InShip) {
                final BlockPos rawTarget = ((PathAccessor) (Object) path).vs$getRawTarget();
                if (rawTarget != null) return rawTarget;
            }
        }
        // World-frame path whose target landed inside a ship's worldAABB on an air cell:
        // the bed lives on the ship at shipyard coords, so project there.
        final BlockPos worldPos = original.call(path);
        if (worldPos == null) return worldPos;
        if (!level.getBlockState(worldPos).isAir()) return worldPos;
        final Ship ship = vs$shipContaining(level, worldPos);
        if (ship == null) return worldPos;
        // Defensive: input is already shipyard (chunk falls inside the ship's claim).
        if (ship.getChunkClaim().contains(worldPos.getX() >> 4, worldPos.getZ() >> 4)) return worldPos;
        final Vector3d shipyard = ship.getTransform().getWorldToShip().transformPosition(
            new Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
            new Vector3d()
        );
        return BlockPos.containing(shipyard.x, shipyard.y, shipyard.z);
    }

    @Unique
    private static Ship vs$shipContaining(final Level level, final BlockPos worldPos) {
        final double cx = worldPos.getX() + 0.5;
        final double cy = worldPos.getY() + 0.5;
        final double cz = worldPos.getZ() + 0.5;
        final Vector3d worldVec = new Vector3d(cx, cy, cz);
        final AABBd probe = new AABBd(cx - 0.5, cy - 0.5, cz - 0.5, cx + 0.5, cy + 0.5, cz + 0.5);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (ship.getWorldAABB().containsPoint(worldVec)) return ship;
        }
        return null;
    }
}
