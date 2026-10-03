package org.valkyrienskies.mod.compat.sodium.light;

import org.joml.primitives.AABBd;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipEmitterSelectionTest {
    @Test
    void selectsByReachInsteadOfGlobalListOrder() {
        final float[] emitters = {1000, 0, 0, 15, 5, 0, 0, 15, 0, 0, 0, 8, 16, 0, 0, 15};
        final int[] selected = new int[128];
        final int size = ShipEmitterSelection.select(emitters, 4, new AABBd(-1, -1, -1, 1, 1, 1),
            selected, new double[128]);
        assertEquals(2, size);
        assertEquals(1, selected[0]);
        assertEquals(2, selected[1]);
    }

    @Test
    void capKeepsStrongestPotentialContributions() {
        final float[] emitters = {0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 15, 0, 0, 0, 14, 0, 0, 0, 3};
        final int[] selected = new int[2];
        assertEquals(2, ShipEmitterSelection.select(emitters, 5, new AABBd(-1, -1, -1, 1, 1, 1),
            selected, new double[2]));
        assertEquals(2, selected[0]);
        assertEquals(3, selected[1]);
    }

    @Test
    void acceptsLightReachingCornerButRejectsSphereOutsideBounds() {
        final float[] emitters = {2, 2, 2, 2, 3, 3, 3, 2};
        final int[] selected = new int[128];
        assertEquals(1, ShipEmitterSelection.select(emitters, 2, new AABBd(-1, -1, -1, 1, 1, 1),
            selected, new double[128]));
        assertEquals(0, selected[0]);
    }
}
