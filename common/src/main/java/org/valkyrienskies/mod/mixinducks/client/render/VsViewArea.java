package org.valkyrienskies.mod.mixinducks.client.render;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;

public interface VsViewArea {
    void vs$removeExtra(int cx, int cy, int cz);

    void vs$clearExtra();

    @Nullable
    ChunkRenderDispatcher.RenderChunk vs$addExtra(BlockPos pso);

    @Nullable
    Vector2d vs$getCameraPosition();

    record ExtraChunk(
        int cx, int cy, int cz,
        ChunkRenderDispatcher.RenderChunk chunk
    ) {}
}
