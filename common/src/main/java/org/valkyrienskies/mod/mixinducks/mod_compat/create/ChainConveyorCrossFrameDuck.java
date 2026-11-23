package org.valkyrienskies.mod.mixinducks.mod_compat.create;

import net.minecraft.core.BlockPos;

public interface ChainConveyorCrossFrameDuck {
    /** Recomputes the stats of connections to conveyors in another frame; returns whether any end moved. */
    boolean vs$refreshCrossFrame();

    /** Chains paid for the connection to another frame at [connection] when it was made, or null. */
    Integer vs$chainsPaid(BlockPos connection);
}
