package org.valkyrienskies.mod.mixin.accessors.server.level;

import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkHolder.class)
public interface ChunkHolderAccessor {
    @Accessor("fullChunkFuture")
    void setFullChunkFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> future);

    @Accessor("fullChunkFuture")
    CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> getFullChunkFuture();

    @Accessor("futures")
    AtomicReferenceArray<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> getFutures();

    @Accessor("chunkToSave")
    CompletableFuture<? extends ChunkAccess> getChunkToSave();

    @Accessor("pendingFullStateConfirmation")
    CompletableFuture<Void> getPendingFullStateConfirmation();

    @Accessor("ticketLevel")
    int getTicketLevel();

    @Accessor("hasChangedSections")
    boolean getHasChangedSections();

    @Accessor("changedBlocksPerSection")
    ShortSet[] getChangedBlocksPerSection();
}
