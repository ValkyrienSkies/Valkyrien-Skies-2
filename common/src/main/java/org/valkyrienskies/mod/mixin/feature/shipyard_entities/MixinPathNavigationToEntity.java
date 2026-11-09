package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// PathNavigation.createPath(Entity, int) reads target.blockPosition() — for shipyard-frame targets (armor stand in shipyard, migrated shulker) this is the ship-local BlockPos and the wrong worldToShip downstream produces a misprojected target. Project shipyard targets to world here so the existing nav frame-resolution handles all combinations.
@Mixin(PathNavigation.class)
public abstract class MixinPathNavigationToEntity {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$projectShipyardTargetToWorld(
        final Entity target, final Operation<BlockPos> original
    ) {
        final BlockPos raw = original.call(target);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(target.level(), raw);
        if (ship == null) return raw;
        final Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(raw.getX() + 0.5, raw.getY(), raw.getZ() + 0.5), VS$OUT.get()
        );
        return BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
    }
}
