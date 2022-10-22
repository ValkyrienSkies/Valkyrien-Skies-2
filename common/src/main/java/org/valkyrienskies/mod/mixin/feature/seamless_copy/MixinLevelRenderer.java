package org.valkyrienskies.mod.mixin.feature.seamless_copy;

import java.util.Set;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.assembly.SeamlessChunksManager;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    private Set<RenderChunk> chunksToCompile;

    @Shadow
    private ChunkRenderDispatcher chunkRenderDispatcher;

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"
        ),
        method = "compileChunksUntil",
        cancellable = true
    )
    private void injectCompileChunksUntil(final long finishTimeNano, final CallbackInfo ci) {
        final SeamlessChunksManager manager = SeamlessChunksManager.get();
        if (manager != null) {
            manager.scheduleLinkedChunksCompile(finishTimeNano, chunksToCompile, chunkRenderDispatcher);
            ci.cancel();
        }
    }

}
