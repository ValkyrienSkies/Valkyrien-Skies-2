package org.valkyrienskies.mod.accessors.client.render;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BuiltChunkStorage.class)
public interface BuiltChunkStorageAccessor {
    @Invoker(value = "getRenderedChunk")
    ChunkBuilder.BuiltChunk callGetRenderedChunk(BlockPos pos);
}
