package org.valkyrienskies.mod.mixin.mod_compat.common_create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = {
    "net.createmod.catnip.placement.PlacementClient",
    "com.simibubi.create.foundation.placement.PlacementHelpers"
})
public class MixinPlacementHelpers {
    @WrapOperation(
        method = "drawDirectionIndicator",
        at = {
            @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"),
            @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
        })
    private static Vec3 redirectGetCenterOf(Vec3i pos, Operation<Vec3> original) {
        return VSGameUtilsKt.toWorldCoordinates(
            Minecraft.getInstance().level, original.call(pos)
        );
    }
}
