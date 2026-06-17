package org.valkyrienskies.mod.mixinducks.client.render;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.jetbrains.annotations.Nullable;

public interface IVSViewAreaMethods {
    void unloadChunk(int chunkX, int chunkZ);

    /**
     * Get a ship render chunk directly by section coordinates, bypassing the
     * getShipManagingPos lookup that the normal getRenderChunkAt path triggers.
     */
    @Nullable
    ChunkRenderDispatcher.RenderChunk vs$getShipRenderChunk(int chunkX, int sectionY, int chunkZ);

    /**
     * Get or create a ship render chunk, marking it dirty. Bypasses getShipManagingPos.
     */
    @Nullable
    ChunkRenderDispatcher.RenderChunk vs$getOrCreateShipRenderChunk(int chunkX, int sectionY, int chunkZ);

    /**
     * Pre-allocate render chunks into the pool to avoid glGenBuffers stalls later.
     * Call once per frame; fills gradually (a few per frame).
     */
    void vs$fillRenderChunkPool();
}
