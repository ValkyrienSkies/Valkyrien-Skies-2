package org.valkyrienskies.mod.mixin.accessors.client.render.chunk;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public interface RenderChunkAccessor {

    @Invoker
    void callReset();

}
