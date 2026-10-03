package org.valkyrienskies.mod.common.render.batched;

import net.minecraft.core.SectionPos;

/** Fixed 32-block batches bound a rebuild to eight sections and keep vertex coordinates small. */
final class ShipMeshBatches {
    static final int SECTIONS_PER_AXIS = 2;
    static final int BLOCKS_PER_AXIS = 16 * SECTIONS_PER_AXIS;

    private ShipMeshBatches() {
    }

    static long key(final int sx, final int sy, final int sz) {
        return SectionPos.asLong(Math.floorDiv(sx, SECTIONS_PER_AXIS),
            Math.floorDiv(sy, SECTIONS_PER_AXIS), Math.floorDiv(sz, SECTIONS_PER_AXIS));
    }
}
