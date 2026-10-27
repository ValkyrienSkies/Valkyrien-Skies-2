package org.valkyrienskies.mod.common.render.light;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.primitives.AABBd;

/** Keep an exact snapshot of the inputs to the shadow buffer. */
final class ShipShadowFrame {
    static final class Entry {
        long shipId;
        List<AABBd> boxes;
        final Matrix4d transform = new Matrix4d();
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private int size;
    private boolean changed;

    void begin() {
        size = 0;
        changed = false;
    }

    void add(final long shipId, final List<AABBd> boxes, final Matrix4dc transform) {
        if (boxes.isEmpty()) return;
        final Entry entry;
        if (size == entries.size()) {
            entry = new Entry();
            entries.add(entry);
            changed = true;
        } else {
            entry = entries.get(size);
        }
        if (entry.shipId != shipId || entry.boxes != boxes || !entry.transform.equals(transform, 0.0)) {
            entry.shipId = shipId;
            entry.boxes = boxes;
            entry.transform.set(transform);
            changed = true;
        }
        size++;
    }

    boolean finish() {
        if (size != entries.size()) {
            entries.subList(size, entries.size()).clear();
            changed = true;
        }
        return changed;
    }

    List<Entry> entries() {
        return entries;
    }

    void clear() {
        entries.clear();
        size = 0;
        changed = false;
    }
}
