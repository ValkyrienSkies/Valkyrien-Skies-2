package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Lets brain behaviors that read tracked-entity positions see shipyard entities at their
// world-rendered location instead of raw shipyard coords.
@Mixin(EntityTracker.class)
public abstract class MixinEntityTracker {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @Shadow @Final private Entity entity;
    @Shadow @Final private boolean trackEyeHeight;

    @Inject(method = "currentPosition", at = @At("HEAD"), cancellable = true)
    private void vs$projectShipyardEntityPositionToWorld(
        final CallbackInfoReturnable<Vec3> cir
    ) {
        final Vector3d worldPos = vs$projectEntityWorldPos();
        if (worldPos == null) return;
        final double y = trackEyeHeight ? worldPos.y + entity.getEyeHeight() : worldPos.y;
        cir.setReturnValue(new Vec3(worldPos.x, y, worldPos.z));
    }

    @Inject(method = "currentBlockPosition", at = @At("HEAD"), cancellable = true)
    private void vs$projectShipyardEntityBlockPositionToWorld(
        final CallbackInfoReturnable<BlockPos> cir
    ) {
        final Vector3d worldPos = vs$projectEntityWorldPos();
        if (worldPos == null) return;
        cir.setReturnValue(BlockPos.containing(worldPos.x, worldPos.y, worldPos.z));
    }

    @Unique
    private Vector3d vs$projectEntityWorldPos() {
        final Level level = entity.level();
        if (level == null) return null;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, entity.blockPosition());
        if (ship == null) return null;
        return ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(entity.getX(), entity.getY(), entity.getZ()), VS$OUT.get()
        );
    }
}
