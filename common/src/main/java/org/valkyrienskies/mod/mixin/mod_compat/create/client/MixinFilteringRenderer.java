package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(FilteringRenderer.class)
public class MixinFilteringRenderer {

    @Redirect(
            method = "renderOnBlockEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    )
    private static double redirectDistanceToSqr(Vec3 instance, Vec3 vec) {
        Vec3 result = VSGameUtilsKt.toShipRenderCoordinates(Minecraft.getInstance().level, vec, instance);
        return result.distanceToSqr(vec);
    }
}
