package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.redstone.link.LinkRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LinkRenderer.class)
public class MixinLinkRenderer {
    @WrapOperation(
            method = "renderOnBlockEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    )
    private static double redirectDistanceToSqr(Vec3 instance, Vec3 vec, Operation<Double> original) {
        Vec3 result = VSGameUtilsKt.toShipRenderCoordinates(Minecraft.getInstance().level, vec, instance);
        return original.call(result, vec);
    }
}
