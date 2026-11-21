package org.valkyrienskies.mod.mixin.feature.transform_particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import org.joml.Matrix4dc;
import org.valkyrienskies.mod.client.ShipParticlePlacement;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Particle.class)
public abstract class MixinParticle {

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Shadow
    public abstract void remove();

    @Shadow
    protected double xd;

    @Shadow
    protected double yd;

    @Shadow
    protected double zd;

    @Shadow
    protected double xo;

    @Shadow
    protected double yo;

    @Shadow
    protected double zo;

    /**
     * See also {@link MixinLevelRenderer}
     */
    @Inject(
        method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDD)V",
        at = @At("TAIL")
    )
    public void checkShipCoords(final ClientLevel world, final double x, final double y, final double z,
        final CallbackInfo ci) {
        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(world, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        if (ship == null) {
            return;
        }
        final var pieces = VSGameUtilsKt.getAllShips(world).getSplitPieces(ship.getId());
        if (!pieces.isEmpty()) {
            final var piece = ShipParticlePlacement.piece(pieces, x, y, z);
            if (piece == null) {
                remove();
                return;
            }
            final var p = piece.getShipToWorld().transformPosition(new Vector3d(x, y, z));
            this.setPos(p.x, p.y, p.z);
            this.xo = p.x;
            this.yo = p.y;
            this.zo = p.z;
            return;
        }
        final Vector3d p = ship.getRenderTransform().getShipToWorld().transformPosition(new Vector3d(x, y, z));
        this.setPos(p.x, p.y, p.z);
        this.xo = p.x;
        this.yo = p.y;
        this.zo = p.z;
    }

    /**
     * See also {@link MixinLevelRenderer}
     */
    @Inject(
        method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V",
        at = @At("TAIL")
    )
    public void checkShipPosAndVelocity(final ClientLevel world, final double x, final double y, final double z,
        final double velocityX,
        final double velocityY, final double velocityZ, final CallbackInfo ci) {

        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(world, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        if (ship == null) {
            return;
        }

        final var pieces = VSGameUtilsKt.getAllShips(world).getSplitPieces(ship.getId());
        if (!pieces.isEmpty()) {
            final var piece = ShipParticlePlacement.piece(pieces, x, y, z);
            if (piece == null) return;
            final var v = ShipParticlePlacement.velocity(piece, ship, xd, yd, zd);
            this.xd = v.x;
            this.yd = v.y;
            this.zd = v.z;
            return;
        }
        final Matrix4dc transform = ship.getRenderTransform().getShipToWorld();
        // in-world velocity
        final Vector3d v = transform
            // Rotate velocity wrt ship transform
            .transformDirection(new Vector3d(this.xd, this.yd, this.zd))
            // Tack on the ships linear velocity (multiplied by 1/20 because particle velocity is given per tick)
            .fma(0.05, ship.getVelocity());

        this.xd = v.x;
        this.yd = v.y;
        this.zd = v.z;
    }

}
