package org.valkyrienskies.mod.mixin.feature.fluid_render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderTypes;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk.RebuildTask;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

@Mixin(RebuildTask.class)
public class MixinRebuildTask {
    @Shadow
    private RenderChunkRegion region;

    @Unique
    private RenderChunkRegion vs$airRenderChunkRegion;

    @Inject(method = "compile", at = @At("HEAD"))
    private void captureRegion(
            float x, float y, float z,
            ChunkBufferBuilderPack pack, CallbackInfoReturnable<?> cir) {
        this.vs$airRenderChunkRegion = region;
    }

    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getRenderLayer(Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType redirectFluidRenderLayer(FluidState state) {
        if (this.vs$airRenderChunkRegion == null)
            return ItemBlockRenderTypes.getRenderLayer(state);
        Level level = ((RenderChunkRegionAccessor) this.vs$airRenderChunkRegion).getLevel();
        int centerX = ((RenderChunkRegionAccessor) this.vs$airRenderChunkRegion).getCenterX();
        int centerZ = ((RenderChunkRegionAccessor) this.vs$airRenderChunkRegion).getCenterZ();
        if (level == null)
            return ItemBlockRenderTypes.getRenderLayer(state);
        if ((state.is(Fluids.WATER) || state.is(Fluids.FLOWING_WATER))
                && VSGameConfig.CLIENT.getUnderwater().getEnableWaterCulling()
                && !VSGameUtilsKt.isChunkInShipyard(level, centerX, centerZ)
            ) {
            return ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE;
        }

        return ItemBlockRenderTypes.getRenderLayer(state);
    }
}
