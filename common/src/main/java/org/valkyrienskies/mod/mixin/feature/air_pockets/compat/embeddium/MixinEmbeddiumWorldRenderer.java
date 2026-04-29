package org.valkyrienskies.mod.mixin.feature.air_pockets.compat.embeddium;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.air_pockets.client.EmbeddiumChunkRenderContext;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCullRenderContext;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class MixinEmbeddiumWorldRenderer {

    @Inject(
        method = "drawChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDD)V",
        at = @At("HEAD"),
        require = 0
    )
    private void valkyrienair$beginWorldTranslucentChunkLayer(final RenderType renderLayer, final PoseStack poseStack,
        final double x, final double y, final double z, final CallbackInfo ci) {
        if (!ShipWaterPocketExternalWaterCullRenderContext.isFluidChunkLayer(renderLayer)) return;
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        EmbeddiumChunkRenderContext.pushChunkCameraWorldPos(x, y, z);
        ShipWaterPocketExternalWaterCullRenderContext.beginWorldFluidChunkLayer(
            level,
            renderLayer,
            EmbeddiumChunkRenderContext.getChunkCamXOr(x),
            EmbeddiumChunkRenderContext.getChunkCamYOr(y),
            EmbeddiumChunkRenderContext.getChunkCamZOr(z)
        );
    }

    @Inject(
        method = "drawChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDD)V",
        at = @At("TAIL"),
        require = 0
    )
    private void valkyrienair$endWorldTranslucentChunkLayer(final RenderType renderLayer, final PoseStack poseStack,
        final double x, final double y, final double z, final CallbackInfo ci) {
        if (!ShipWaterPocketExternalWaterCullRenderContext.isFluidChunkLayer(renderLayer)) return;
        ShipWaterPocketExternalWaterCullRenderContext.endWorldFluidChunkLayer();
        EmbeddiumChunkRenderContext.pop();
    }
}
