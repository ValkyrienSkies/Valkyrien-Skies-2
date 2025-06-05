package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.AirFlowParticle;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.Vec3i;
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


    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"), allow = 1)
    private Vec3 redirectGetCenterOf(Vec3i pos) {
        Ship ship = getShip();
        Vec3 result = VecHelper.getCenterOf(pos);
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformPosition(result.x, result.y, result.z, tempVec);
            result = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return result;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"), allow = 1)
    private Vec3 redirectToLowerCorner(Vec3i pos) {
        Vec3 result = Vec3.atLowerCornerOf(pos);
        Ship ship = getShip();
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformDirection(result.x, result.y, result.z, tempVec);
            result = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return result;
    }
}
