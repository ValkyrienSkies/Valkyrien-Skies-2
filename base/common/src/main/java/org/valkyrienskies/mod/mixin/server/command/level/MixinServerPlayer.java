package org.valkyrienskies.mod.mixin.server.command.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Shadow
    public abstract ServerLevel getLevel();

    @Shadow
    public abstract void teleportTo(double d, double e, double f);

    @Inject(
        at = @At("HEAD"),
        method = "teleportTo(DDD)V",
        cancellable = true
    )
    private void beforeTeleportTo(final double x, final double y, final double z, final CallbackInfo ci) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(getLevel(), x, y, z);
        if (ship != null) {
            ci.cancel();
            final Vector3d inWorld = VSGameUtilsKt.toWorldCoordinates(ship, x, y, z);
            this.teleportTo(inWorld.x, inWorld.y, inWorld.z);
        }
    }

    @Inject(
        at = @At("HEAD"),
        method = "dismountTo",
        cancellable = true
    )
    private void beforeDismountTo(final double x, final double y, final double z, final CallbackInfo ci) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(getLevel(), x, y, z);
        if (ship != null) {
            ci.cancel();
            final Vector3d inWorld = VSGameUtilsKt.toWorldCoordinates(ship, x, y, z);
            this.teleportTo(inWorld.x, inWorld.y, inWorld.z);
        }
    }
}
