package org.valkyrienskies.mod.mixinducks.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;

public interface LevelChunkDuck {

    Object2IntMap<BlockPos> vs_getLights();
}
