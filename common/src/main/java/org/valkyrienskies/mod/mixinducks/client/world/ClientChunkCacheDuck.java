package org.valkyrienskies.mod.mixinducks.client.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface ClientChunkCacheDuck {
    Long2ObjectMap<LevelChunk> vs$getShipChunks();
    void vs$removeShip(ClientShip ship);

    interface StorageDuck {
        void vs$incChunkCount();
        void vs$decChunkCount();
    }
}
