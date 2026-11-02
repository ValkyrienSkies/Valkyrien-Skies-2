package org.valkyrienskies.mod.mixin.world.ticks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

/**
 * Trace-only diagnostic for scheduled-tick dispatch on shipyard chunks.
 *
 * Enabled by {@code -Dvs.traceScheduledTicks=true}. When on, logs every time
 * {@code sortContainersToTick} evaluates {@code tickCheck} for a shipyard chunk —
 * including the result — so we can see whether ship chunks are reaching the drain
 * stage or being filtered out earlier.
 *
 * Logging runs ONLY when the system property is set; with it unset the wrapper is
 * a single bitmap check and falls through to the unchanged vanilla call.
 */
@Mixin(LevelTicks.class)
public abstract class MixinLevelTicks<T> {

    @Unique
    private static final boolean VS$TRACE_SCHEDULED_TICKS =
        Boolean.getBoolean("vs.traceScheduledTicks");

    @Unique
    private static final Logger VS$TRACE_LOG = LoggerFactory.getLogger("VS2-TickTrace");

    @Shadow
    @Final
    private Long2ObjectMap<LevelChunkTicks<T>> allContainers;

    @Shadow
    @Final
    private Long2LongMap nextTickForContainer;

    @WrapOperation(
        method = "sortContainersToTick",
        at = @At(value = "INVOKE", target = "Ljava/util/function/LongPredicate;test(J)Z")
    )
    private boolean vs$tracedTickCheck(
        final java.util.function.LongPredicate self, final long chunkPosLong,
        final Operation<Boolean> op) {
        final boolean result = op.call(self, chunkPosLong);
        if (VS$TRACE_SCHEDULED_TICKS) {
            int cx = ChunkPos.getX(chunkPosLong);
            int cz = ChunkPos.getZ(chunkPosLong);
            if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
                VS$TRACE_LOG.info(
                    "sortContainersToTick tickCheck shipyard chunk=({},{}) result={}",
                    cx, cz, result);
            }
        }
        return result;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vs$traceTick(final long gameTime, final int maxTicks,
                               final java.util.function.BiConsumer<net.minecraft.core.BlockPos, T> tickOp,
                               final CallbackInfo ci) {
        if (VS$TRACE_SCHEDULED_TICKS) {
            // Log tick() invocations with a summary of nextTickForContainer's shipyard entries
            int shipyardEntries = 0;
            long earliestShipyard = Long.MAX_VALUE;
            for (Long2LongMap.Entry e : it.unimi.dsi.fastutil.longs.Long2LongMaps.fastIterable(this.nextTickForContainer)) {
                long key = e.getLongKey();
                int cx = ChunkPos.getX(key);
                int cz = ChunkPos.getZ(key);
                if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
                    shipyardEntries++;
                    if (e.getLongValue() < earliestShipyard) {
                        earliestShipyard = e.getLongValue();
                    }
                }
            }
            if (shipyardEntries > 0) {
                VS$TRACE_LOG.info(
                    "LevelTicks.tick gameTime={} shipyardEntries={} earliest={} tickInstance={}",
                    gameTime, shipyardEntries, earliestShipyard, System.identityHashCode(this));
            }
        }
    }

    @Inject(method = "schedule", at = @At("HEAD"))
    private void vs$traceSchedule(final ScheduledTick<?> tick, final CallbackInfo ci) {
        if (VS$TRACE_SCHEDULED_TICKS) {
            int cx = tick.pos().getX() >> 4;
            int cz = tick.pos().getZ() >> 4;
            if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
                VS$TRACE_LOG.info(
                    "LevelTicks.schedule shipyard tick pos={} type={} triggerTick={}",
                    tick.pos(), tick.type(), tick.triggerTick());
            }
        }
    }

    @Inject(method = "addContainer", at = @At("HEAD"))
    private void vs$traceAddContainer(final ChunkPos pos, final LevelChunkTicks<?> container, final CallbackInfo ci) {
        if (VS$TRACE_SCHEDULED_TICKS) {
            if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) {
                VS$TRACE_LOG.info(
                    "LevelTicks.addContainer shipyard chunk=({},{}) containerId={} peek={}",
                    pos.x, pos.z, System.identityHashCode(container), container.peek());
            }
        }
    }

    @Inject(method = "removeContainer", at = @At("HEAD"))
    private void vs$traceRemoveContainer(final ChunkPos pos, final CallbackInfo ci) {
        if (VS$TRACE_SCHEDULED_TICKS) {
            if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) {
                VS$TRACE_LOG.info("LevelTicks.removeContainer shipyard chunk=({},{})", pos.x, pos.z);
            }
        }
    }

    @Inject(method = "updateContainerScheduling", at = @At("HEAD"))
    private void vs$traceUpdateScheduling(final ScheduledTick<?> tick, final CallbackInfo ci) {
        if (VS$TRACE_SCHEDULED_TICKS) {
            int cx = tick.pos().getX() >> 4;
            int cz = tick.pos().getZ() >> 4;
            if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
                VS$TRACE_LOG.info(
                    "LevelTicks.updateContainerScheduling shipyard tick pos={} triggerTick={}",
                    tick.pos(), tick.triggerTick());
            }
        }
    }

    /**
     * After any successful schedule on a shipyard chunk, force-sync nextTickForContainer
     * from the container's earliest pending tick.
     *
     * LevelChunkTicks normally relies on the onTickAdded callback (chunkScheduleUpdater)
     * to keep nextTickForContainer in sync. That callback only updates when the new tick
     * becomes the top of the queue — but on shipyard chunks we've observed cases where
     * the first scheduled tick lands in the container without the entry ever reaching
     * nextTickForContainer, so LevelTicks.sortContainersToTick never drains it. Without
     * that drain, repeaters, buttons, water, and dispensers silently stop working.
     *
     * This TAIL inject is idempotent: if the callback already updated the map, put() just
     * writes the same value. Scoped to shipyard chunks to avoid changing vanilla behavior.
     */
    @Inject(method = "schedule", at = @At("TAIL"))
    private void vs$forceNextTickSync(final ScheduledTick<T> tick, final CallbackInfo ci) {
        final int cx = tick.pos().getX() >> 4;
        final int cz = tick.pos().getZ() >> 4;
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
            return;
        }
        final long chunkPosLong = ChunkPos.asLong(cx, cz);
        final LevelChunkTicks<T> container = this.allContainers.get(chunkPosLong);
        if (container == null) {
            if (VS$TRACE_SCHEDULED_TICKS) {
                VS$TRACE_LOG.info("forceSync: no container for shipyard chunk=({},{})", cx, cz);
            }
            return;
        }
        final ScheduledTick<T> peek = container.peek();
        if (peek == null) {
            if (VS$TRACE_SCHEDULED_TICKS) {
                VS$TRACE_LOG.info("forceSync: container has no peek for shipyard chunk=({},{})", cx, cz);
            }
            return;
        }
        final long existing = this.nextTickForContainer.get(chunkPosLong);
        final long newTrigger = peek.triggerTick();
        final boolean willUpdate = existing == this.nextTickForContainer.defaultReturnValue()
            || newTrigger < existing;
        if (willUpdate) {
            this.nextTickForContainer.put(chunkPosLong, newTrigger);
        }
        if (VS$TRACE_SCHEDULED_TICKS) {
            VS$TRACE_LOG.info(
                "forceSync: shipyard chunk=({},{}) existing={} newTrigger={} updated={}",
                cx, cz, existing, newTrigger, willUpdate);
        }
    }
}
