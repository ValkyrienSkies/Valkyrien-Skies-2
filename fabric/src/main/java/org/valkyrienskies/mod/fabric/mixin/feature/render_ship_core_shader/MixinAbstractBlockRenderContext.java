package org.valkyrienskies.mod.fabric.mixin.feature.render_ship_core_shader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// IDE plugin malds about it being important but... :nerd:
@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public class MixinAbstractBlockRenderContext {
    // This is far simpler than whatever we do in common :/
    @WrapOperation(method = {"shadeFlatQuad", "shadeQuad"}, at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/helper/ColorHelper;multiplyRGB(IF)I"))
    int smuggleNoShade(int color, float shade, Operation<Integer> original) {
        if (shade > 4f) {
            return original.call(color, shade - 4f) & 0x00FFFFFF; // offset brightness back and zero out alpha
        }
        return original.call(color, shade);
    }
}
