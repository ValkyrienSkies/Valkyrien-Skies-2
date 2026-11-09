package org.valkyrienskies.mod.common.render.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.function.Predicate;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet;
import org.valkyrienskies.core.api.util.functions.IntBinaryConsumer;

/** Keep geometry in ship coordinates until its blocks or bounds change. */
public final class ShipGeometryCache<T> {
    private record Entry<T>(AABBi bounds, int chunkCount, long[] chunks, T value, long tick, boolean dynamic) { }
    private final Long2ObjectOpenHashMap<Entry<T>> entries = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet present = new LongOpenHashSet();
    private Object level;
    private long revision;
    private final LongArrayList chunkScratch = new LongArrayList();
    private final IntBinaryConsumer collectChunk = (x, z) -> chunkScratch.add(((long) x << 32) | (z & 0xffffffffL));

    public synchronized void beginFrame(final Object currentLevel) {
        if (level != currentLevel) {
            entries.clear();
            level = currentLevel;
        }
        present.clear();
    }

    public synchronized T get(final long shipId, final AABBic bounds, final int chunkCount,
        final Supplier<T> collect) {
        return get(shipId, bounds, chunkCount, 0L, value -> false, collect);
    }

    public synchronized T get(final long shipId, final AABBic bounds, final int chunkCount,
        final long tick, final Predicate<T> dynamic, final Supplier<T> collect) {
        return get(shipId, bounds, chunkCount, null, tick, dynamic, collect);
    }

    public synchronized T get(final long shipId, final AABBic bounds, final IShipActiveChunksSet chunks,
        final Supplier<T> collect) {
        return get(shipId, bounds, chunks, 0L, value -> false, collect);
    }

    public synchronized T get(final long shipId, final AABBic bounds, final IShipActiveChunksSet chunks,
        final long tick, final Predicate<T> dynamic, final Supplier<T> collect) {
        return get(shipId, bounds, chunks.getSize(), chunks, tick, dynamic, collect);
    }

    private T get(final long shipId, final AABBic bounds, final int chunkCount,
        final IShipActiveChunksSet chunks, final long tick, final Predicate<T> dynamic, final Supplier<T> collect) {
        present.add(shipId);
        chunkScratch.clear();
        if (chunks != null) chunks.forEach(collectChunk);
        final Entry<T> entry = entries.get(shipId);
        if (entry != null && (!entry.dynamic || entry.tick == tick) && entry.chunkCount == chunkCount
            && (chunks == null ? entry.chunks == null : entry.chunks != null
                && Arrays.equals(entry.chunks, 0, entry.chunks.length, chunkScratch.elements(), 0, chunkScratch.size()))
            && (entry.bounds == null ? bounds == null : entry.bounds.equals(bounds))) {
            return entry.value;
        }
        final long before = revision;
        final long[] chunkCopy = chunks == null ? null : chunkScratch.toLongArray();
        final T value = collect.get();
        if (before == revision) {
            entries.put(shipId, new Entry<>(bounds == null ? null : new AABBi(bounds), chunkCount, chunkCopy,
                value, tick, dynamic.test(value)));
        }
        return value;
    }

    public synchronized void invalidate(final long shipId) {
        revision++;
        entries.remove(shipId);
    }

    public synchronized void endFrame() {
        final var iterator = entries.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            if (!present.contains(iterator.next().getLongKey())) iterator.remove();
        }
    }

    public synchronized void clear() {
        revision++;
        entries.clear();
        present.clear();
        level = null;
    }
}
