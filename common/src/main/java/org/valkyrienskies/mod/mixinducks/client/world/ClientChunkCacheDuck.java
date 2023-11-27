package org.valkyrienskies.mod.mixinducks.client.world;

import io.netty.util.collection.LongObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;

public interface ClientChunkCacheDuck {
    LongObjectMap<LevelChunk> vs$getShipChunks();
}
