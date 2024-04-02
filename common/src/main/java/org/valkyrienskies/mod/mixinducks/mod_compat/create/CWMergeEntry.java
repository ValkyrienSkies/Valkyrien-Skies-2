package org.valkyrienskies.mod.mixinducks.mod_compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * This class only exists because Creates MergeEntry class is private
 */
public class CWMergeEntry {
    public final Direction.Axis axis;
    public final BlockPos pos;

    public CWMergeEntry(Direction.Axis axis, BlockPos pos) {
        this.axis = axis;
        this.pos = pos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CWMergeEntry other))
            return false;

        return this.axis == other.axis && this.pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode() * 31 + axis.ordinal();
    }
}
