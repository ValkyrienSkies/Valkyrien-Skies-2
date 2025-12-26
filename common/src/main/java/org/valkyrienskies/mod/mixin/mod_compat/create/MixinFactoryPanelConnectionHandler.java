package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(FactoryPanelConnectionHandler.class)
public class MixinFactoryPanelConnectionHandler {

    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
    private static boolean valkyrien_logistics$stupidDistanceCheck(BlockPos instance, Vec3i vec3i, double v, Operation<Boolean> original, @Local Minecraft mc) {
        ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(mc.level, instance);

        Vector3d jomlInstance = VectorConversionsMCKt.toJOML(instance.getCenter());

        if (ship != null)
            ship.getTransform().getShipToWorld().transformPosition(jomlInstance);

        ship = VSGameUtilsKt.getLoadedShipManagingPos(mc.level, vec3i);
        Vector3d jomlVec3i = VectorConversionsMCKt.toJOMLD(vec3i);
        if (ship != null)
            ship.getTransform().getShipToWorld().transformPosition(jomlVec3i);

        return original.call(
            BlockPos.containing(VectorConversionsMCKt.toMinecraft(jomlInstance)),
            BlockPos.containing(jomlVec3i.x, jomlVec3i.y, jomlVec3i.z),
            v
        );
    }
}
