package org.valkyrienskies.mod.forge.mixin.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

import com.mojang.blaze3d.vertex.PoseStack;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.renderer.RenderType;

@Mixin(value = SodiumWorldRenderer.class, remap = false, priority = 1100)
public abstract class MixinSodiumWorldRenderer {
    @Shadow
    private RenderSectionManager renderSectionManager;

    /**
     * Draws the world-fluid cull pass inside the translucent layer, so a shaderpack draws it as water.
     *
     * <p>Iris brackets {@code LevelRenderer.renderChunkLayer} with
     * {@code setPhase(fromTerrainRenderType(layer))}, and everything it binds — the water program and
     * the translucent render targets — follows from that phase. Calling
     * {@link RenderSectionManager#renderLayer} goes straight to Sodium and never passes through that
     * hook, so this pass simply inherits whichever phase happens to be current. Running it here, still
     * inside the translucent layer, is what makes it inherit the water pass rather than having to
     * rebind anything: it reuses the buffers Iris already set up.</p>
     *
     * <p>Tripwire is the wrong place for exactly that reason. It is the last chunk layer, so the
     * geometry lands after everything else, but by then the phase is {@code TRIPWIRE} and the fluid is
     * drawn with the wrong program into the wrong targets.</p>
     */
    @Inject(method = "drawChunkLayer", at = @At("TAIL"))
    private void afterChunkLayer(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z,
            CallbackInfo ci) {
        if (renderLayer == RenderType.translucent() && VSGameConfig.CLIENT.getUnderwater().getEnableWaterCulling()) {
            renderSectionManager.renderLayer(
                ChunkRenderMatrices.from(matrixStack), SodiumCompat.AIR_POCKET_PASS, x, y, z);
        }
        SodiumCompat.renderShips(renderSectionManager, renderLayer, ChunkRenderMatrices.from(matrixStack), x, y, z);
    }
}
