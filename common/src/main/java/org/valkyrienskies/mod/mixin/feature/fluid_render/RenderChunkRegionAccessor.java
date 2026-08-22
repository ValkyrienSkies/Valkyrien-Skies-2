package org.valkyrienskies.mod.mixin.feature.fluid_render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;

@Mixin(RenderChunkRegion.class)
public interface RenderChunkRegionAccessor {
    @Accessor("centerX")
    int getCenterX();

    @Accessor("centerZ")
    int getCenterZ();

    @Accessor("level")
    Level getLevel();
}