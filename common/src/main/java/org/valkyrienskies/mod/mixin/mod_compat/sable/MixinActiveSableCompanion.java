package org.valkyrienskies.mod.mixin.mod_compat.sable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ActiveSableCompanion.class)
public class MixinActiveSableCompanion {
    @ModifyReturnValue(method = "projectOutOfSubLevel(Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", at = @At(
        value = "RETURN", ordinal = 0))
    private Vector3d returnVSShipToWorldPos(Vector3d original, Level level) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, original);
        if (ship != null)
            return ship.getTransform().getShipToWorld().transformPosition(original);
        return original;
    }

    @ModifyReturnValue(method = "projectOutOfSubLevel(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;)Lnet/minecraft/world/phys/Vec3;", at = @At(
        value = "RETURN", ordinal = 0))
    private Vec3 returnVSShipToWorldPos(Vec3 original, Level level) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, original);
        if (ship != null)
            return VectorConversionsMCKt.toMinecraft(ship.getTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(original)));
        return original;
    }

    @ModifyReturnValue(method = "getVelocity(Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
        at = @At(value = "RETURN", ordinal = 0))
    private Vector3d returnShipVel(Vector3d original, Level level) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, original);
        if (ship != null)
            return original.set(ship.getVelocity());
        return original;
    }

    @WrapOperation(method = "getFeetPos", at = @At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/entity/EntitySubLevelUtil;getCustomEntityOrientation(Lnet/minecraft/world/entity/Entity;F)Lorg/joml/Quaterniondc;"))
    private Quaterniondc returnShipRot(Entity entity, float partialTicks, Operation<Quaterniondc> original) {
        Quaterniondc ret = original.call(entity, partialTicks);
        if (ret == null) {
            Ship s = VSGameUtilsKt.getShipManagingPos(entity.level(), entity.position());
            if (s == null)
                return null;
            ret = s.getTransform().getRotation();
        }
        return ret;
    }
}
