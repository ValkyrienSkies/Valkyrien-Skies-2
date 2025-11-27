package org.valkyrienskies.mod.forge.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.SodiumCompat;

import com.mojang.blaze3d.vertex.PoseStack;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.renderer.RenderType;

@Mixin(value = SodiumWorldRenderer.class, remap = false, priority = 1100)
public abstract class MixinSodiumWorldRenderer {
    @Shadow
    private RenderSectionManager renderSectionManager;

    @Inject(method = "drawChunkLayer", at = @At("TAIL"))
    private void afterChunkLayer(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z,
            CallbackInfo ci) {
        SodiumCompat.renderShips(renderSectionManager, renderLayer, ChunkRenderMatrices.from(matrixStack), x, y, z);
    }
}
