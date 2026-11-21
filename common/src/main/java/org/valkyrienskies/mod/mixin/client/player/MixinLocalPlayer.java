package org.valkyrienskies.mod.mixin.client.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.MountedCameraRotation;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.player.LocalPlayerDuck;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends LivingEntity implements LocalPlayerDuck {
    @Unique
    private Vec3 lastPosition = null;
    @Unique
    private Vector3dc velocity = new Vector3d();
    @Unique
    private final MountedCameraRotation vs$mountedCameraRotation = new MountedCameraRotation();

    protected MixinLocalPlayer() {
        super(null, null);
    }

    /**
     * @reason We need to overwrite this method to force Minecraft to smoothly interpolate the Y rotation of the player
     * during rendering. Why it wasn't like this originally is beyond me \(>.<)/
     * @author StewStrong
     */
    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void preGetViewYRot(final float partialTick, final CallbackInfoReturnable<Float> cir) {
        if (this.isPassenger()) {
            cir.setReturnValue(super.getViewYRot(partialTick));
        } else {
            cir.setReturnValue(Mth.lerp(partialTick, this.yRotO, this.getYRot()));
        }
    }

    @Override
    public Vector3dc vs$getVelocity() {
        return this.velocity;
    }

    @Override
    public MountedCameraRotation vs$getMountedCameraRotation() {
        return this.vs$mountedCameraRotation;
    }

    @Override
    public void vs$updateMountedCameraRotation(final boolean render) {
        final Entity vehicle = this.getVehicle();
        if (vehicle == null || !(VSGameUtilsKt.getShipMountedTo(this) instanceof ClientShip ship)) {
            this.vs$mountedCameraRotation.clear();
            return;
        }
        this.vs$mountedCameraRotation.update(vehicle, ship.getId(),
            (render ? ship.getRenderTransform() : ship.getTransform()).getRotation(), this.getYRot(), this.getXRot());
        this.vs$applyMountedCameraRotation();
    }

    @Override
    public void turn(final double yaw, final double pitch) {
        this.vs$updateMountedCameraRotation(true);
        if (!this.vs$mountedCameraRotation.isMounted()) {
            super.turn(yaw, pitch);
            return;
        }
        this.vs$mountedCameraRotation.turn(yaw, pitch);
        this.vs$applyMountedCameraRotation();
        this.getVehicle().onPassengerTurned(this);
    }

    @Unique
    private void vs$applyMountedCameraRotation() {
        this.setYRot(this.vs$mountedCameraRotation.getWorldYaw());
        this.setXRot(this.vs$mountedCameraRotation.getWorldPitch());
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.yHeadRot = this.getYRot();
        this.yHeadRotO = this.yHeadRot;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(final CallbackInfo ci) {
        this.vs$updateMountedCameraRotation(false);
        final Vec3 pos = this.position();
        if (this.lastPosition != null) {
            this.velocity = new Vector3d(pos.x - this.lastPosition.x, pos.y - this.lastPosition.y, pos.z - this.lastPosition.z);
        }
        this.lastPosition = pos;
    }
}
