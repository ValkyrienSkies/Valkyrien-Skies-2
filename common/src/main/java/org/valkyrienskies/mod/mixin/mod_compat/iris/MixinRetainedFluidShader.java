package org.valkyrienskies.mod.mixin.mod_compat.iris;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.RetainedFluidRenderer;
import org.valkyrienskies.mod.compat.flooding.shaders.NativeFluidCompat;

@Mixin(VertexBuffer.class)
public abstract class MixinRetainedFluidShader {
    @Inject(method = "_drawWithShader", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V", shift = At.Shift.AFTER))
    private void vs$allowRetainedFluidColor(final Matrix4f view, final Matrix4f projection,
        final ShaderInstance shader, final CallbackInfo ci) {
        if (RetainedFluidRenderer.isShader(shader)) DepthColorStorage.unlockDepthColor();
        NativeFluidCompat.apply(shader);
    }
}
