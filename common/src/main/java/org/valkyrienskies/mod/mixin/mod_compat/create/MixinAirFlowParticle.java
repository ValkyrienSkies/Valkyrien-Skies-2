package org.valkyrienskies.mod.mixin.mod_compat.create;

import org.valkyrienskies.mod.common.world.RayQuery;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.AirFlowParticle;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IExtendedAirCurrentSource;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(AirFlowParticle.class)
public abstract class MixinAirFlowParticle extends SimpleAnimatedParticle {

    @Unique private ClientShip vs$fanShip;
    @Unique private Vector3d vs$previousLocal;
    @Unique private Vector3d vs$currentLocal;

    @Shadow
    @Final
    private IAirCurrentSource source;

    protected MixinAirFlowParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float gravity) {
        super(level, x, y, z, sprites, gravity);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/kinetics/fan/AirFlowParticle;setPos(DDD)V"))
    private void vs$anchorSpawn(AirFlowParticle particle, double x, double y, double z, Operation<Void> original) {
        Ship sourceShip = getShip();
        if (sourceShip instanceof ClientShip ship && VSGameUtilsKt.getAllShips(level).getSplitPieces(ship.getId()).isEmpty()) {
            vs$fanShip = ship;
            vs$currentLocal = ship.getRenderTransform().getWorldToShip().transformPosition(x, y, z, new Vector3d());
            vs$previousLocal = new Vector3d(vs$currentLocal);
            Vector3d tick = ship.getShipToWorld().transformPosition(vs$currentLocal, new Vector3d());
            original.call(particle, tick.x, tick.y, tick.z);
        } else if (sourceShip != null) {
            // Keep the existing split-piece path: there is no unique canonical frame for the fan.
            Vector3d point = sourceShip.getPrevTickTransform().getWorldToShip().transformPosition(x, y, z, new Vector3d());
            sourceShip.getShipToWorld().transformPosition(point);
            original.call(particle, point.x, point.y, point.z);
        } else {
            original.call(particle, x, y, z);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vs$advanceWithFan(CallbackInfo ci) {
        if (vs$fanShip == null) return;
        if (getShip() != vs$fanShip || !VSGameUtilsKt.getAllShips(level).getSplitPieces(vs$fanShip.getId()).isEmpty()) {
            vs$fanShip = null;
            return;
        }
        vs$previousLocal.set(vs$currentLocal);
        Vector3d point = vs$fanShip.getShipToWorld().transformPosition(vs$currentLocal, new Vector3d());
        setPos(point.x, point.y, point.z);
    }

    @Unique
    private Ship getShip() {
        // Edge case on loading the fan too quickly, source is null
        if (source == null) return null;

        if (source instanceof IExtendedAirCurrentSource se)
            return se.getShip();
        else if (source.getAirCurrentWorld() != null)
            return VSGameUtilsKt.getShipManagingPos(source.getAirCurrentWorld(), source.getAirCurrentPos());
        else
            return null;
    }

    /**
     * By the time AirFlowParticle logic is called, particle position has already been transformed from ship to world.
     * However, code for this particle refers to coordinates of a shipspace block ("air current source", really a fan)
     * needing manual adjustment.
     */
    @Redirect(method = "tick", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/phys/AABB;contains(DDD)Z"
    ))
    private boolean redirectBounds(AABB instance, double x, double y, double z) {
        AirCurrent current = source.getAirCurrent();
        Level level = source.getAirCurrentWorld();
        Ship ship = getShip();
        if (current != null && level != null && ship != null) {
            Vector3d tempPos = ship.getTransform().getWorldToShip().transformPosition(x, y, z, new Vector3d());
            return instance.contains(tempPos.x, tempPos.y, tempPos.z);
        }

        return instance.contains(x, y, z);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 transformPosToShip(Vec3 instance, Vec3 vec3, Operation<Vec3> original) {
        Ship ship = getShip();
        if (ship != null) {
            instance = VectorConversionsMCKt.toMinecraft(
                ship.getWorldToShip().transformPosition(
                    VectorConversionsMCKt.toJOML(instance)
                )
            );
        }
        return original.call(instance, vec3);
    }

    /**
     * We need to preserve the original direction vector for distance calculations that happen in shipspace.
     * Only transforming it to world when calculating the motion vector of a worldspace particle.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private Vec3 transformDirectionForMotion(Vec3 dir, double d, Operation<Vec3> original) {
        Ship ship = getShip();
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformDirection(dir.x, dir.y, dir.z, tempVec);
            dir = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return original.call(dir, d);
    }

    /**
     * Not many particles need to be 'dragged' by the ship, but airflow particles are indicator of something that is fixed to the ship.
     * Therefore, this logic is similar to that of entity dragging feature.
     */
    @WrapOperation(
        method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/AirFlowParticle;move(DDD)V")
    )
    private void moveWithShip(AirFlowParticle particle, double x, double y, double z, Operation<Void> original){
        original.call(particle, x, y, z);
        if (vs$fanShip != null) {
            // Create has just moved the particle using this same tick pose. Its local motion and
            // the ship's motion can now be sampled independently when rendering.
            vs$fanShip.getWorldToShip().transformPosition(this.x, this.y, this.z, vs$currentLocal);
            return;
        }
        Ship ship = getShip();
        if (ship != null) {
            Vector3d p = new Vector3d(this.x, this.y, this.z);
            ship.getPrevTickTransform().getWorldToShip().transformPosition(p);
            ship.getTransform().getShipToWorld().transformPosition(p);
            this.xd = p.x - this.xo;
            this.yd = p.y - this.yo;
            this.zd = p.z - this.zo;
            this.setPos(p.x, p.y, p.z);
        }
    }

    @Override
    public AABB getBoundingBox() {
        AABB tick = super.getBoundingBox();
        Float t = RayQuery.frameTime();
        if (vs$fanShip == null || t == null) return tick;
        Vector3d p = vs$fanShip.getRenderTransform().getShipToWorld().transformPosition(
            vs$previousLocal.lerp(vs$currentLocal, t, new Vector3d()));
        return tick.minmax(tick.move(p.x - x, p.y - y, p.z - z).inflate(getQuadSize(t)));
    }

    @Override
    public void render(VertexConsumer vertices, Camera camera, float partialTick) {
        if (vs$fanShip == null || !VSGameUtilsKt.getAllShips(level).getSplitPieces(vs$fanShip.getId()).isEmpty()) {
            super.render(vertices, camera, partialTick);
            return;
        }
        Vector3d frame = vs$previousLocal.lerp(vs$currentLocal, partialTick, new Vector3d());
        vs$fanShip.getRenderTransform().getShipToWorld().transformPosition(frame);
        double px = x, py = y, pz = z, ox = xo, oy = yo, oz = zo;
        try {
            x = xo = frame.x;
            y = yo = frame.y;
            z = zo = frame.z;
            super.render(vertices, camera, partialTick);
        } finally {
            x = px; y = py; z = pz;
            xo = ox; yo = oy; zo = oz;
        }
    }
}
