package org.valkyrienskies.mod.mixinducks.world;

import net.minecraft.core.BlockPos;

public interface WithSealedPositions {
    public void setSealed(BlockPos pos, boolean sealed);

    public boolean[] getSealed();
}
