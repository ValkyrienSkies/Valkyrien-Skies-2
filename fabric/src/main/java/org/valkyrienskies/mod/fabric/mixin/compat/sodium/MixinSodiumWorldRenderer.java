package org.valkyrienskies.mod.fabric.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.renderer.RenderType;

@Mixin(value = SodiumWorldRenderer.class, remap = false, priority = 1100)
public abstract class MixinSodiumWorldRenderer {
    @Shadow
    private RenderSectionManager renderSectionManager;

    @Inject(method = "drawChunkLayer", at = @At("TAIL"))
    private void afterChunkLayer(RenderType renderLayer, ChunkRenderMatrices matrices, double x, double y, double z,
            CallbackInfo ci) {
            // Inside the translucent layer on purpose: Iris binds the water program and the translucent
            // render targets from the phase it sets around renderChunkLayer, and renderLayer bypasses
            // that hook, so the pass has to run here to inherit those buffers. See the Forge copy of
            // this mixin for the full reasoning.
            if (renderLayer == RenderType.translucent() && VSGameConfig.CLIENT.getUnderwater().getEnableWaterCulling()) {
                renderSectionManager.renderLayer(matrices, SodiumCompat.AIR_POCKET_PASS, x, y, z);
            }
            SodiumCompat.renderShips(renderSectionManager, renderLayer, matrices, x, y, z);
    }
}
