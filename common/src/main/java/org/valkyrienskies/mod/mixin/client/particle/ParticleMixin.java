package org.valkyrienskies.mod.mixin.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Particle.class)
public abstract class ParticleMixin {

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Shadow
    protected double velocityX;

    @Shadow
    protected double velocityY;

    @Shadow
    protected double velocityZ;

    /**
     * See also {@link org.valkyrienskies.mod.mixin.client.render.MixinWorldRenderer}
     */
    @Inject(
        method = "Lnet/minecraft/client/particle/Particle;<init>(Lnet/minecraft/client/world/ClientWorld;DDD)V",
        at = @At("TAIL")
    )
    public void checkShipCoords(final ClientWorld world, final double x, final double y, final double z,
        final CallbackInfo ci) {
        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(world, (int) x >> 4, (int) z >> 4);
        if (ship == null) {
            return;
        }

        // in-world position
        final Vector3d p = ship.getRenderTransform().getShipToWorldMatrix().transformPosition(new Vector3d(x, y, z));
        this.setPos(p.x, p.y, p.z);
    }

    /**
     * See also {@link org.valkyrienskies.mod.mixin.client.render.MixinWorldRenderer}
     */
    @Inject(
        method = "Lnet/minecraft/client/particle/Particle;<init>(Lnet/minecraft/client/world/ClientWorld;DDDDDD)V",
        at = @At("TAIL")
    )
    public void checkShipPosAndVelocity(final ClientWorld world, final double x, final double y, final double z,
        final double velocityX,
        final double velocityY, final double velocityZ, final CallbackInfo ci) {

        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(world, (int) x >> 4, (int) z >> 4);
        if (ship == null) {
            return;
        }

        final Matrix4dc transform = ship.getRenderTransform().getShipToWorldMatrix();
        // in-world position
        final Vector3d p = transform.transformPosition(new Vector3d(x, y, z));
        // in-world velocity
        final Vector3d v = transform
            // Rotate velocity wrt ship transform
            .transformDirection(new Vector3d(this.velocityX, this.velocityY, this.velocityZ))
            // Tack on the ships linear velocity (no angular velocity param unfortunately)
            .add(ship.getShipData().getPhysicsData().getLinearVelocity());

        this.setPos(p.x, p.y, p.z);
        this.velocityX = v.x;
        this.velocityY = v.y;
        this.velocityZ = v.z;
    }

}
