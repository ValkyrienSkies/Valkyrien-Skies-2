package org.valkyrienskies.mod.mixin.mod_compat.optifine;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * This mixin lets us create new {@link LevelRenderer.RenderChunkInfo} objects.
 */
@Mixin(LevelRenderer.RenderChunkInfo.class)
public interface RenderChunkInfoAccessorOptifine {

    /**
     * This mixin allows us to invoke the private constructor of WorldRenderer.ChunkInfo.
     * <p>
     * Optifine changes this constructor from synthetic to static, for seemingly no reason -_-
     * <p>
     * Oh well, just add a new Mixin to handle it.
     */
    @Invoker(value = "<init>", remap = false)
    static LevelRenderer.RenderChunkInfo vs$new(
        final ChunkRenderDispatcher.RenderChunk chunk,
        @Nullable final Direction direction,
        final int propagationLevel
    ) {
        throw new AssertionError("RenderChunkInfoAccessorOptifine failed to apply");
    }
}
