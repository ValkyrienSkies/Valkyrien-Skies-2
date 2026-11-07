package org.valkyrienskies.mod.common.fluid.client;

import java.util.BitSet;

/**
 * Cell predicates over a {@link ShipFluidRenderSnapshot}'s parallel bit sets.
 *
 * <p>All indices are flat grid indices, {@code lx + sizeX * (ly + sizeY * lz)}.</p>
 */
public final class ShipFluidCellClassification {

    private ShipFluidCellClassification() {
    }

    /** A cell of surrounding world fluid: outside the ship's domain, but wet. */
    public static boolean isOutsideSubmergedFluid(final BitSet open, final BitSet interior,
        final BitSet waterReachable, final int idx) {
        return open.get(idx) && !interior.get(idx) && waterReachable.get(idx);
    }

    /** A cell inside the ship's domain that fluid could occupy. */
    public static boolean isInteriorOpen(final BitSet open, final BitSet interior, final int idx) {
        return open.get(idx) && interior.get(idx);
    }

    /**
     * Whether any of the cell's six neighbours is an open domain cell.
     *
     * <p>Run over world-fluid cells, this picks out the shell where the surrounding fluid meets the
     * ship's interior — the faces the cull has to account for.</p>
     */
    public static boolean touchesInteriorOpen(final BitSet open, final BitSet interior, final int idx,
        final int sizeX, final int sizeY, final int sizeZ) {
        final int lx = idx % sizeX;
        final int t = idx / sizeX;
        final int ly = t % sizeY;
        final int lz = t / sizeY;
        final int strideY = sizeX;
        final int strideZ = sizeX * sizeY;

        if (lx > 0 && isInteriorOpen(open, interior, idx - 1)) return true;
        if (lx + 1 < sizeX && isInteriorOpen(open, interior, idx + 1)) return true;
        if (ly > 0 && isInteriorOpen(open, interior, idx - strideY)) return true;
        if (ly + 1 < sizeY && isInteriorOpen(open, interior, idx + strideY)) return true;
        if (lz > 0 && isInteriorOpen(open, interior, idx - strideZ)) return true;
        return lz + 1 < sizeZ && isInteriorOpen(open, interior, idx + strideZ);
    }
}
