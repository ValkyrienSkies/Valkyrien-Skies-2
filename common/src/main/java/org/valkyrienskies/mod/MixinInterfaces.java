package org.valkyrienskies.mod;

import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.joml.Matrix4dc;

public final class MixinInterfaces {

    public interface ISendsChunkWatchPackets {
        void vs$sendWatchPackets(ServerPlayerEntity player, ChunkPos pos, Packet<?>[] packets);
    }

    public interface ISetMatrix4fFromJOML {
        void vs$setFromJOML(Matrix4dc matrix4dc);
    }
}