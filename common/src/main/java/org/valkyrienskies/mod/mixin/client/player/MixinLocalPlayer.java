package org.valkyrienskies.mod.mixin.client.player;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.client.player.LocalPlayerDuck;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends LivingEntity implements LocalPlayerDuck {
    @Shadow
    private float yRotLast;
    @Shadow
    private float xRotLast;
    @Unique
    private Vec3 lastPosition = null;
    @Unique
    private Vector3dc velocity = new Vector3d();

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

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(final CallbackInfo ci) {
        final Vec3 pos = this.position();
        if (this.lastPosition != null) {
            this.velocity = new Vector3d(pos.x - this.lastPosition.x, pos.y - this.lastPosition.y, pos.z - this.lastPosition.z);
        }
        this.lastPosition = pos;
    }

    @WrapMethod(
        method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"
    )
    private boolean adjustLookOnMount(Entity entity, boolean bl, Operation<Boolean> original) {
        Vector3d lookVector = VectorConversionsMCKt.toJOML(this.getLookAngle());
        if(original.call(entity, bl)) {
            Ship ship = VSGameUtilsKt.getShipMountedTo(Entity.class.cast(this));
            if (ship != null) {
                final Vector3d transformedLook = ship.getTransform().getWorldToShip().transformDirection(lookVector);
                final double yaw = Math.atan2(-transformedLook.x, transformedLook.z) * 180.0 / Math.PI;
                final double pitch = Math.atan2(-transformedLook.y, Math.sqrt((transformedLook.x * transformedLook.x) + (transformedLook.z * transformedLook.z))) * 180.0 / Math.PI;
                this.setYRot((float) yaw);
                this.setXRot((float) pitch);
                this.yRotO = this.getYRot();
                this.yRotLast = this.getYRot();
                this.yHeadRot = this.getYRot();
                this.yHeadRotO = this.getYRot();
                this.xRotO = this.getXRot();
                this.xRotLast = this.getXRot();
            }
            return true;
        }
        return false;
    }
}
