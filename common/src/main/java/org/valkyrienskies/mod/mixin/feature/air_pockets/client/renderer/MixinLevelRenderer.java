package org.valkyrienskies.mod.mixin.feature.air_pockets.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCullRenderContext;

// Some renderers can overwrite LevelRenderer's chunk-layer rendering, which makes INVOKE-based injections into that
// method fragile. We track active world *fluid* passes here and drive shader uniform updates from ShaderInstance#apply.
@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class MixinLevelRenderer {

    @Shadow
    private @Nullable ClientLevel level;

    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void valkyrienair$beginWorldTranslucentChunkLayer(final RenderType renderType,
        final PoseStack poseStack, final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final CallbackInfo ci) {
        if (this.level == null) return;
        if (!ShipWaterPocketExternalWaterCullRenderContext.isFluidChunkLayer(renderType)) return;
        ShipWaterPocketExternalWaterCullRenderContext.beginWorldFluidChunkLayer(this.level, renderType, camX, camY, camZ);
    }

    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void valkyrienair$endWorldTranslucentChunkLayer(final RenderType renderType,
        final PoseStack poseStack, final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final CallbackInfo ci) {
        if (!ShipWaterPocketExternalWaterCullRenderContext.isFluidChunkLayer(renderType)) return;
        ShipWaterPocketExternalWaterCullRenderContext.endWorldFluidChunkLayer();
    }
}
