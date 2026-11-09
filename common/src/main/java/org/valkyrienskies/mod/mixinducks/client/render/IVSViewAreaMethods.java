package org.valkyrienskies.mod.mixinducks.client.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public interface IVSViewAreaMethods {
    SectionRenderDispatcher.RenderSection vs$getShipRenderSection(int chunkX, int sectionY, int chunkZ);
    SectionRenderDispatcher.RenderSection vs$getOrCreateShipRenderSection(int chunkX, int sectionY, int chunkZ);
    void vs$fillRenderChunkPool();
    void valkyrienskies$unloadChunk(int chunkX, int chunkZ);
}
