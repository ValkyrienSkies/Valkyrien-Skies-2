package org.valkyrienskies.mod.mixin.feature.seamless_copy;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk.ChunkCompileTask;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.ChunkCompileTaskDuck;

@Mixin(ChunkCompileTask.class)
public class MixinChunkCompileTask implements ChunkCompileTaskDuck {

    @Shadow
    @Final
    RenderChunk field_20837;

    @Override
    public @NotNull RenderChunk vs_getRenderChunk() {
        return this.field_20837;
    }

}
