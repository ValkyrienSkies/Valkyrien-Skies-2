package org.valkyrienskies.mod.mixin.server.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

/**
 * Safety-net fix for the "Saving World" hang.
 *
 * MinecraftServer.stopServer() loops on chunkMap.hasWork() until all chunk work is drained.
 * If shipyard ChunkHolders get stuck in updatingChunkMap (because their tickets weren't fully
 * removed or the distance manager didn't schedule them for dropping), this override prevents
 * an infinite loop by excluding shipyard-only entries from the hasWork() check.
 *
 * The primary fix is in MixinMinecraftServer.preStopServer() which removes SHIP_CHUNK tickets.
 * This mixin is a defense-in-depth measure.
 */
@Mixin(ChunkMap.class)
public class MixinChunkMapClose {

    private static final Logger LOGGER = LoggerFactory.getLogger("VS2-ShutdownDiag");

    @Shadow @Final private ThreadedLevelLightEngine lightEngine;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;
    @Shadow @Final private PoiManager poiManager;
    @Shadow @Final private it.unimi.dsi.fastutil.longs.LongSet toDrop;
    @Shadow @Final private java.util.Queue<Runnable> unloadQueue;
    @Shadow @Final private ChunkTaskPriorityQueueSorter queueSorter;
    @Shadow @Final ChunkMap.DistanceManager distanceManager;

    @Inject(method = "hasWork", at = @At("HEAD"), cancellable = true)
    private void vs$hasWorkSkipShipyard(CallbackInfoReturnable<Boolean> cir) {
        if (updatingChunkMap.isEmpty()) return;

        // Quick check: if updatingChunkMap has entries, see if they're ALL shipyard chunks.
        // If so, evaluate hasWork() without the updatingChunkMap condition.
        boolean hasNonShipyard = false;
        for (var entry : updatingChunkMap.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
                hasNonShipyard = true;
                break;
            }
        }

        if (!hasNonShipyard) {
            // All entries are shipyard chunks. Check remaining conditions only.
            boolean result = lightEngine.hasLightWork()
                || !pendingUnloads.isEmpty()
                || poiManager.hasWork()
                || !toDrop.isEmpty()
                || !unloadQueue.isEmpty()
                || queueSorter.hasWork();
            // Note: we intentionally skip distanceManager.hasTickets() here because
            // shipyard ticket entries may linger in the tickets map even after removal.
            cir.setReturnValue(result);
        }
    }
}
