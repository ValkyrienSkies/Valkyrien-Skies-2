package org.valkyrienskies.mod.mixin.accessors.client.render;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * This mixin lets us create new {@link net.minecraft.client.render.WorldRenderer.ChunkInfo} objects.
 */
@Mixin(WorldRenderer.ChunkInfo.class)
public interface WorldRendererChunkInfoAccessor {

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
    static WorldRenderer.ChunkInfo vs$new(final WorldRenderer worldRenderer, final ChunkBuilder.BuiltChunk chunk,
        @Nullable final Direction direction, final int propagationLevel) {
        throw new AssertionError("MixinWorldRendererChunkInfo failed to apply");
    }
}
