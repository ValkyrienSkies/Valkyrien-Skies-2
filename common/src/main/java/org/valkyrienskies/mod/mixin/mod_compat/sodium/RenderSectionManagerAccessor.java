package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;

@Mixin(RenderSectionManager.class)
public interface RenderSectionManagerAccessor {
    @Accessor("chunkRenderer")
    ChunkRenderer getChunkRenderer();
}
