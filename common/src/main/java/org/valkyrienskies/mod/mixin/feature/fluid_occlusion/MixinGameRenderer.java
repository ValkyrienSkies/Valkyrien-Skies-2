package org.valkyrienskies.mod.mixin.feature.fluid_occlusion;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.ExteriorFluidFog;

@Mixin(value = GameRenderer.class, priority = 900)
public abstract class MixinGameRenderer {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
        shift = At.Shift.AFTER))
    private void vs$exteriorFogAfterWorld(final CallbackInfo ci) { ExteriorFluidFog.render(); }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"), require = 0)
    private void vs$exteriorFogBeforeHand(final CallbackInfo ci) { ExteriorFluidFog.render(); }
}
