package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * This mixin lets us create new {@link net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo} objects.
 */
@Mixin(LevelRenderer.RenderChunkInfo.class)
public interface RenderChunkInfoAccessor {

    /**
     * This mixin allows us to invoke the private constructor of WorldRenderer.ChunkInfo.
     *
     * <p>The compiler complains about it because it thinks the constructor arguments are
     * (ChunkBuilder.BuiltChunk, Direction, int), but at runtime the constructor arguments are actually (WorldRenderer,
     * ChunkBuilder.BuiltChunk, Direction, int). This is caused by weird synthetic behavior of non-static inner
     * classes.
     *
     * <p>The easy fix for this problem is to use a wildcard target.
     */
    @Invoker(value = "<init>")
    static LevelRenderer.RenderChunkInfo vs$new(
        final LevelRenderer worldRenderer,
        final ChunkRenderDispatcher.RenderChunk chunk,
        @Nullable final Direction direction,
        final int propagationLevel
    ) {
        throw new AssertionError("RenderChunkInfoAccessor failed to apply");
    }
}
