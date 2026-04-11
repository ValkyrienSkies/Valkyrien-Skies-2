package org.valkyrienskies.mod.mixin.accessors.server.level;

import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    @Invoker("clearCache")
    void callClearCache();

    @Invoker("runDistanceManagerUpdates")
    boolean callRunDistanceManagerUpdates();

    /**
     * Start the chunk loading pipeline for a chunk without blocking.
     * Returns a future that completes when the chunk reaches the requested status.
     * Use this to kick off many chunk loads concurrently before waiting for any of them.
     */
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>
        callGetChunkFutureMainThread(int x, int z, ChunkStatus status, boolean create);
}
