package org.valkyrienskies.mod.mixin.accessors.client.render.chunk;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public interface RenderChunkAccessor {

    @Invoker
    void callReset();

    @Accessor("bb")
    void vs$setBb(AABB bb);

}
