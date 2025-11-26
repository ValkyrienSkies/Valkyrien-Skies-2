package org.valkyrienskies.mod.mixinducks.client.world;

import io.netty.util.collection.LongObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface ClientChunkCacheDuck {
    LongObjectMap<LevelChunk> vs$getShipChunks();
    void vs$removeShip(ClientShip ship);
}
