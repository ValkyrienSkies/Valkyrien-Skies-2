package org.valkyrienskies.mod.mixin.world.level.lighting;

import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

import java.util.function.IntSupplier;

/**
 * Forces the light engine to process light updates for shipyard chunks at high priority.
 *
 * ThreadedLevelLightEngine.checkBlock() (and other methods) submit tasks through a
 * ChunkTaskPriorityQueueSorter that uses chunkMap.getChunkQueueLevel() to prioritize.
 * For shipyard chunks without a proper ChunkHolder, this returns MAX_LEVEL+1 priority,
 * causing light tasks to sit at the bottom of the queue and never get processed.
 *
 * This mixin intercepts the getChunkQueueLevel call in addTask and returns FULL chunk
 * priority (level 33) for shipyard positions, ensuring light updates are processed.
 */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class MixinThreadedLevelLightEngine {

    private static final int VS$FULL_CHUNK_LEVEL = ChunkLevel.byStatus(FullChunkStatus.FULL);

    /**
     * Redirect the chunkMap.getChunkQueueLevel() call inside addTask(int,int,TaskType,Runnable)
     * so that shipyard chunks get high priority instead of whatever the ChunkMap returns.
     */
    @Redirect(
        method = "addTask(IILnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;getChunkQueueLevel(J)Ljava/util/function/IntSupplier;")
    )
    private IntSupplier vs$forceShipyardPriority(ChunkMap instance, long chunkPosLong) {
        int x = ChunkPos.getX(chunkPosLong);
        int z = ChunkPos.getZ(chunkPosLong);
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(x, z)) {
            return () -> VS$FULL_CHUNK_LEVEL;
        }
        return ((org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor) instance)
            .callGetChunkQueueLevel(chunkPosLong);
    }
}
