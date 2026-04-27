package org.valkyrienskies.mod.forge.mixin.compat.create.packets;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.glue.SuperGlueRemovalPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SuperGlueRemovalPacket.class)
public abstract class MixinSuperGlueRemovalPacket {
    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double redirectPlayerDistanceToSqr(final ServerPlayer instance, final Vec3 vec3, Operation<Double> original) {
        Vec3 newVec3 = vec3;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(instance.level(), vec3);
        if (ship != null) {
            newVec3 = VectorConversionsMCKt.toMinecraft(
                ship.getShipToWorld().transformPosition(new Vector3d(vec3.x, vec3.y, vec3.z)));
        }
        return original.call(instance, newVec3);
    }
}
