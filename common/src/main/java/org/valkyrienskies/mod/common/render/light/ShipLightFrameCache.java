package org.valkyrienskies.mod.common.render.light;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import org.joml.Matrix4dc;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet;
import org.valkyrienskies.core.api.util.functions.IntBinaryConsumer;

/** Reuse a completed light grid only when all its inputs are unchanged. */
public final class ShipLightFrameCache {
    private static final class Entry {
        Object ship;
        long id;
        AABBi bounds;
        long[] chunks = new long[0];
        final long[] transform = new long[16];
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private final LongArrayList chunks = new LongArrayList();
    private final IntBinaryConsumer collectChunk = (x, z) -> chunks.add(((long) x << 32) | (z & 0xffffffffL));
    private final double[] matrix = new double[16];
    private final long[] matrixBits = new long[16];
    private Object world;
    private Object storage;
    private long revision;
    private int cursor;
    private boolean valid;
    private boolean matches;
    private boolean cacheable;
    private boolean prepared;

    public void begin(final Object world, final Object storage, final long revision) {
        matches = valid && this.world == world && this.storage == storage && this.revision == revision;
        valid = false;
        prepared = false;
        cacheable = true;
        cursor = 0;
        this.world = world;
        this.storage = storage;
        this.revision = revision;
    }

    public void add(final Object ship, final long id, final AABBic bounds, final Matrix4dc transform,
        final IShipActiveChunksSet activeChunks) {
        chunks.clear();
        activeChunks.forEach(collectChunk);
        transform.get(matrix);
        for (int i = 0; i < matrix.length; i++) {
            matrixBits[i] = Double.doubleToRawLongBits(matrix[i]);
            if (!Double.isFinite(matrix[i])) cacheable = false;
        }
        final Entry entry;
        if (cursor == entries.size()) {
            entry = new Entry();
            entries.add(entry);
            matches = false;
        } else {
            entry = entries.get(cursor);
        }
        cursor++;
        final boolean same = entry.ship == ship && entry.id == id
            && (entry.bounds == null ? bounds == null : bounds != null && entry.bounds.equals(bounds))
            && Arrays.equals(entry.transform, matrixBits)
            && Arrays.equals(entry.chunks, 0, entry.chunks.length, chunks.elements(), 0, chunks.size());
        if (!same) {
            matches = false;
            entry.ship = ship;
            entry.id = id;
            entry.bounds = bounds == null ? null : new AABBi(bounds);
            entry.chunks = chunks.toLongArray();
            System.arraycopy(matrixBits, 0, entry.transform, 0, matrixBits.length);
        }
    }

    public boolean finish() {
        if (cursor != entries.size()) matches = false;
        while (entries.size() > cursor) entries.remove(entries.size() - 1);
        prepared = true;
        valid = matches && cacheable;
        return valid;
    }

    /** Call only after collection, propagation, pruning, and uploads complete. */
    public void commit(final boolean allowed) {
        valid = prepared && cacheable && allowed;
    }

    public void clear() {
        entries.clear();
        chunks.clear();
        world = null;
        storage = null;
        valid = false;
        prepared = false;
    }
}
