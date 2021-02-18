package org.valkyrienskies.mod;

import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;

public final class MixinInterfaces {

    public interface ISendsChunkWatchPackets {
        void vs$sendWatchPackets(ServerPlayerEntity player, ChunkPos pos, Packet<?>[] packets);
    }
}