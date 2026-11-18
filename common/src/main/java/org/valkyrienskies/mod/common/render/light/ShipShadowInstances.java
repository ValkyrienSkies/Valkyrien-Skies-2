package org.valkyrienskies.mod.common.render.light;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** Keep box data in ship coordinates. Update only the matrices when ships move. */
final class ShipShadowInstances {
    static final int BOX_BYTES = 32;
    private ByteBuffer boxes = allocate(BOX_BYTES);
    private ByteBuffer transforms = allocate(48);
    private int count;

    ByteBuffer boxes() {
        return boxes;
    }

    ByteBuffer transforms() {
        return transforms;
    }

    int count() {
        return count;
    }

    void update(final List<ShipShadowFrame.Entry> entries, final boolean geometryChanged,
        final ShipShadowMapLayout layout) {
        if (geometryChanged) {
            count = 0;
            for (final var entry : entries) count = Math.addExact(count, entry.boxes.size());
            boxes = capacity(boxes, Math.multiplyExact(count, BOX_BYTES));
            boxes.clear();
        }
        transforms = capacity(transforms, Math.multiplyExact(entries.size(), 48));
        transforms.clear();
        for (int index = 0; index < entries.size(); index++) {
            final var entry = entries.get(index);
            // An anchor keeps large shipyard coordinates out of the float box data.
            final var anchor = entry.boxes.get(0);
            final double x = anchor.minX, y = anchor.minY, z = anchor.minZ;
            if (geometryChanged) for (final var box : entry.boxes) {
                boxes.putFloat((float) ((box.minX + box.maxX) * 0.5 - x))
                    .putFloat((float) ((box.minY + box.maxY) * 0.5 - y))
                    .putFloat((float) ((box.minZ + box.maxZ) * 0.5 - z)).putFloat(index);
                boxes.putFloat((float) ((box.maxX - box.minX) * 0.5))
                    .putFloat((float) ((box.maxY - box.minY) * 0.5))
                    .putFloat((float) ((box.maxZ - box.minZ) * 0.5)).putFloat(0);
            }
            final var m = entry.transform;
            transforms.putFloat((float) m.m00()).putFloat((float) m.m10()).putFloat((float) m.m20())
                .putFloat((float) (m.m00() * x + m.m10() * y + m.m20() * z + m.m30() - layout.originX));
            transforms.putFloat((float) m.m01()).putFloat((float) m.m11()).putFloat((float) m.m21())
                .putFloat((float) (m.m01() * x + m.m11() * y + m.m21() * z + m.m31() - layout.originY));
            transforms.putFloat((float) m.m02()).putFloat((float) m.m12()).putFloat((float) m.m22())
                .putFloat((float) (m.m02() * x + m.m12() * y + m.m22() * z + m.m32() - layout.originZ));
        }
        if (geometryChanged) boxes.flip();
        transforms.flip();
    }

    void clear() {
        if (count == 0 && boxes.capacity() == BOX_BYTES && transforms.capacity() == 48) return;
        boxes = allocate(BOX_BYTES);
        transforms = allocate(48);
        count = 0;
    }

    private static ByteBuffer allocate(final int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer capacity(final ByteBuffer buffer, final int bytes) {
        if (buffer.capacity() >= bytes) return buffer;
        return allocate((int) Math.min(Integer.MAX_VALUE, Math.max(bytes, buffer.capacity() * 2L)));
    }
}
