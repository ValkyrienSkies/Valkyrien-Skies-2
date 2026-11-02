package org.valkyrienskies.mod.mixin.feature.render_ship_core_shader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(RenderChunkRegion.class)
public abstract class MixinRenderChunkRegion {
    @Shadow @Final private int centerX;
    @Shadow @Final private int centerZ;
    @Shadow @Final protected Level level;

    // Vanilla "shading" is applied in vertex colors when building the chunk. We disable this pre-baking as we replicate
    // it later in the custom core shader.
    // Shading should not be confused with lighting or ambient occlusion which we avoid breaking.
    @WrapMethod(method = "getShade")
    private float maxShadeForShips(Direction direction, boolean bl, Operation<Float> original) {
        if (VSGameConfig.CLIENT.getBetterVanillaShipShading() && VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(centerX, centerZ))
            // Because we force a 1f (or 0.9f, if in Nether) shading value, as if the quad was exempt from shading,
            // we cannot discern quads that actually need this special treatment.
            // The workaround is to offset the brightness value and detect it later in the chunk building pipeline.
            return (bl ? original.call(direction, false) : original.call(direction, false) + 4f);
        return original.call(direction, bl);
    }
}
