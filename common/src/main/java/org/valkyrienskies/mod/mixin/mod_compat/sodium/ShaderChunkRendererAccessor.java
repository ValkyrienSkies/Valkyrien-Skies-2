package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

@Mixin(ShaderChunkRenderer.class)
public interface ShaderChunkRendererAccessor {
    @Accessor("programs")
    Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> getPrograms();

    @Accessor("vertexType")
    ChunkVertexType getVertexType();

    @Accessor("activeProgram")
    GlProgram<ChunkShaderInterface> getActiveProgram();

    @Accessor("activeProgram")
    void setActiveProgram(GlProgram<ChunkShaderInterface> program);

    @Invoker("begin")
    void invokeBegin(TerrainRenderPass renderPass);
}
