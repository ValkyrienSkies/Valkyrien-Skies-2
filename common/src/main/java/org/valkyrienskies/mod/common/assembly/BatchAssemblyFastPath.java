package org.valkyrienskies.mod.common.assembly;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Collection;
import net.minecraft.world.level.ChunkPos;

/**
 * Registry of chunk positions (as {@link ChunkPos#toLong()} longs) that gates the
 * server-side batch-assembly chunk fast path in
 * {@code org.valkyrienskies.mod.mixin.feature.batch_assembly_fast_path.MixinChunkMap}.
 *
 * <p>That is safe only when we <em>know</em> the chunks are fresh — no disk data, no
 * pre-existing block state — which is exactly the case for the destination chunks of
 * {@link ShipAssembler#batchAssembleToShips}'s preload phase: ship chunk claims come from
 * a monotonic allocator (never reused), and a brand-new claim has never been saved, so the
 * chunk cannot exist on disk by construction.
 *
 *
 * <p>Thread safety: registration/unregistration happens on the server thread, but chunk
 * pipeline steps may query from worker threads, so all access to the underlying set is
 * synchronized. {@link #isActive()} is a volatile read only — mixin call sites use it to
 * bail out with near-zero overhead whenever no batch assembly is in flight (i.e. almost
 * always).
 */
public final class BatchAssemblyFastPath {

    private static volatile boolean active = false;

    private static final LongOpenHashSet REGISTERED = new LongOpenHashSet();

    private BatchAssemblyFastPath() {}

    /** True if any chunk is currently registered. Cheap static check — volatile read only. */
    public static boolean isActive() {
        return active;
    }

    public static boolean isRegistered(final long chunkPos) {
        if (!active) {
            return false;
        }
        synchronized (REGISTERED) {
            return REGISTERED.contains(chunkPos);
        }
    }

    public static void register(final long chunkPos) {
        synchronized (REGISTERED) {
            REGISTERED.add(chunkPos);
            active = true;
        }
    }

    public static void unregister(final long chunkPos) {
        synchronized (REGISTERED) {
            REGISTERED.remove(chunkPos);
            active = !REGISTERED.isEmpty();
        }
    }

    public static void registerAll(final Collection<ChunkPos> chunkPositions) {
        if (chunkPositions.isEmpty()) {
            return;
        }
        synchronized (REGISTERED) {
            for (final ChunkPos pos : chunkPositions) {
                REGISTERED.add(pos.toLong());
            }
            active = !REGISTERED.isEmpty();
        }
    }

    public static void unregisterAll(final Collection<ChunkPos> chunkPositions) {
        if (chunkPositions.isEmpty()) {
            return;
        }
        synchronized (REGISTERED) {
            for (final ChunkPos pos : chunkPositions) {
                REGISTERED.remove(pos.toLong());
            }
            active = !REGISTERED.isEmpty();
        }
    }

    public static void clear() {
        synchronized (REGISTERED) {
            REGISTERED.clear();
            active = false;
        }
    }
}
