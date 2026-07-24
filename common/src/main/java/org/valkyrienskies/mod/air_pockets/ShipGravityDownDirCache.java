package org.valkyrienskies.mod.air_pockets;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Small per-thread cache for computing a ship's local "down" direction in world-space.
 *
 * <p>This intentionally lives outside the mixin package so mixin-applied classes may safely reference it.
 */
public final class ShipGravityDownDirCache {
    public Level lastLevel = null;
    public long lastGameTime = Long.MIN_VALUE;
    public long lastPosLong = Long.MIN_VALUE;
    public Direction lastDown = null;
}

