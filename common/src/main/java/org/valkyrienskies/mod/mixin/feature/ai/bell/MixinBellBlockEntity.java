package org.valkyrienskies.mod.mixin.feature.ai.bell;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// BellBlockEntity.updateEntities's 48-inflated entity search and per-entity 32-block
// closerToCenterThan check both use the bell's BlockPos, which lives at shipyard coords
// for a ship-mounted bell — the AABB scans empty space and the proximity check always
// rejects. Project both reads through the owning ship's shipToWorld.
@Mixin(BellBlockEntity.class)
public abstract class MixinBellBlockEntity {

    @WrapOperation(
        method = "updateEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        )
    )
    private List<LivingEntity> vs$projectSearchAabbForShipBell(
        final Level level, final Class<LivingEntity> clazz, final AABB shipyardAabb,
        final Operation<List<LivingEntity>> original
    ) {
        final BlockPos bellPos = ((BellBlockEntity) (Object) this).getBlockPos();
        final Ship ship = level == null ? null : VSGameUtilsKt.getShipManagingPos(level, bellPos);
        if (ship == null) return original.call(level, clazz, shipyardAabb);

        final Vector3d worldCenter = ship.getTransform().getShipToWorld().transformPosition(
            new Vector3d(bellPos.getX() + 0.5, bellPos.getY() + 0.5, bellPos.getZ() + 0.5),
            new Vector3d()
        );
        final AABB worldAabb = new AABB(
            worldCenter.x - 48.0, worldCenter.y - 48.0, worldCenter.z - 48.0,
            worldCenter.x + 48.0, worldCenter.y + 48.0, worldCenter.z + 48.0
        );
        final List<LivingEntity> worldHits = original.call(level, clazz, worldAabb);
        final List<LivingEntity> shipyardHits = original.call(level, clazz, shipyardAabb);
        if (shipyardHits.isEmpty()) return worldHits;
        if (worldHits.isEmpty()) return shipyardHits;
        final List<LivingEntity> union = new ArrayList<>(worldHits.size() + shipyardHits.size());
        union.addAll(worldHits);
        for (final LivingEntity ent : shipyardHits) {
            if (!union.contains(ent)) union.add(ent);
        }
        return union;
    }

    @WrapOperation(
        method = "updateEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$projectBellPosForProximity(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original
    ) {
        final Level level = ((BellBlockEntity) (Object) this).getLevel();
        if (level == null) return original.call(instance, position, dist);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, instance);
        if (ship == null) return original.call(instance, position, dist);

        // Project cell center (not corner — toWorldCoordinates(level, BlockPos) projects
        // bellPos.toJOMLD(), which is the integer corner and drifts up to ~0.5+ blocks per
        // axis on rotated ships against a 32-block proximity radius).
        final Vector3d worldCenter = ship.getTransform().getShipToWorld().transformPosition(
            new Vector3d(instance.getX() + 0.5, instance.getY() + 0.5, instance.getZ() + 0.5),
            new Vector3d()
        );
        return original.call(
            BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z), position, dist
        );
    }
}
