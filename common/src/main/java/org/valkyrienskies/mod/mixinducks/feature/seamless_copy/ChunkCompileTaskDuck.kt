package org.valkyrienskies.mod.mixinducks.feature.seamless_copy

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk

interface ChunkCompileTaskDuck {

    fun vs_getRenderChunk(): RenderChunk
}
