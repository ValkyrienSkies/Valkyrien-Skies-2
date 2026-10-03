package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightLutTest {
    @Test
    void movingOneSectionDoesNotRetainTravelHistory() {
        final LightLut lut = new LightLut();
        long previous = SectionPos.asLong(0, 0, 0);
        lut.add(previous, 7);
        for (int i = 1; i <= 10000; i++) {
            final long next = SectionPos.asLong(i, -i, i);
            lut.add(next, 7);
            lut.remove(previous);
            final int[] flat = lut.flatten();
            assertEquals(9, flat.length);
            assertEquals(8, lookup(flat, i, -i, i));
            previous = next;
        }
        lut.remove(previous);
        assertArrayEquals(new int[] {0, 0}, lut.flatten());
    }

    @Test
    void pruningAndReusingScratchPreservesHolesAndNegativeCoordinates() {
        final LightLut lut = new LightLut();
        final IntArrayList scratch = new IntArrayList();
        lut.add(SectionPos.asLong(-3, -2, -5), 0);
        lut.add(SectionPos.asLong(-3, -2, -1), 3);
        lut.add(SectionPos.asLong(2, 2, 2), 9);
        lut.flattenInto(scratch);
        assertEquals(1, lookup(scratch.toIntArray(), -3, -2, -5));
        assertEquals(0, lookup(scratch.toIntArray(), -3, -2, -3));
        lut.remove(SectionPos.asLong(-3, -2, -5));
        lut.remove(SectionPos.asLong(2, 2, 2));
        lut.flattenInto(scratch);
        assertEquals(9, scratch.size());
        assertEquals(4, lookup(scratch.toIntArray(), -3, -2, -1));
        assertEquals(0, lookup(scratch.toIntArray(), 2, 2, 2));
        lut.add(SectionPos.asLong(-3, -2, -5), 6);
        lut.flattenInto(scratch);
        assertEquals(0, lookup(scratch.toIntArray(), -3, -2, -3));
        assertEquals(7, lookup(scratch.toIntArray(), -3, -2, -5));
    }

    private static int lookup(final int[] lut, final int x, final int y, final int z) {
        int base = 0;
        for (final int coordinate : new int[] {y, x, z}) {
            final int offset = coordinate - lut[base];
            if (offset < 0 || offset >= lut[base + 1]) return 0;
            base = lut[base + 2 + offset];
            if (base == 0) return 0;
        }
        return base;
    }
}
