package org.valkyrienskies.mod.mixinducks.feature.seamless_copy;

import java.util.List;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk.ChunkCompileTask;

public interface ChunkRenderDispatcherDuck {
    void vs_scheduleLinked(final List<ChunkCompileTask> tasks);
}
