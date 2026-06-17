package org.valkyrienskies.mod.mixin.feature.ai.swim;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// getRandomSwimmablePos validates each candidate via isPathfindable(WATER) at the WORLD
// cell; for a fish in a ship-mounted tank the world cell is air, the 10-retry loop fails,
// and the swim goal stalls. Project the candidate into nearby ships and accept if any
// projected shipyard cell holds water.
@Mixin(BehaviorUtils.class)
public abstract class MixinBehaviorUtilsSwim {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "getRandomSwimmablePos",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isPathfindable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/pathfinder/PathComputationType;)Z"
        )
    )
    private static boolean vs$shipAwareSwimmable(
        final BlockState worldState, final BlockGetter blockGetter, final BlockPos worldPos,
        final PathComputationType type, final Operation<Boolean> original,
        @Local(argsOnly = true) final PathfinderMob mob
    ) {
        if (original.call(worldState, blockGetter, worldPos, type)) return true;
        if (type != PathComputationType.WATER) return false;
        final Level level = mob.level();
        if (level == null) return false;

        final double cx = worldPos.getX() + 0.5;
        final double cy = worldPos.getY() + 0.5;
        final double cz = worldPos.getZ() + 0.5;
        final Vector3d worldCenter = VS$CENTER.get().set(cx, cy, cz);
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        final Vector3d local = VS$LOCAL.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            ship.getTransform().getWorldToShip().transformPosition(worldCenter, local);
            final BlockPos shipyardPos = BlockPos.containing(local.x, local.y, local.z);
            // getFluidState catches both water blocks and waterlogged blocks without
            // re-running the full isPathfindable logic on the shipyard cell.
            if (level.getBlockState(shipyardPos).getFluidState().is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }
}
