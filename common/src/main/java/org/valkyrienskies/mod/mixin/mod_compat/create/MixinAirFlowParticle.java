package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.AirFlowParticle;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
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
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IExtendedAirCurrentSource;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(AirFlowParticle.class)
public abstract class MixinAirFlowParticle extends SimpleAnimatedParticle {

    @Shadow
    @Final
    private IAirCurrentSource source;

    protected MixinAirFlowParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float gravity) {
        super(level, x, y, z, sprites, gravity);
    }

    @Unique
    private Ship getShip() {
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
        if (current != null && level != null) {
            return VSGameUtilsKt.transformAabbToWorld(level, instance).contains(x, y, z);
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
}
