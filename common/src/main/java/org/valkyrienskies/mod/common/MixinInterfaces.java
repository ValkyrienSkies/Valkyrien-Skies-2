package org.valkyrienskies.mod.common;

import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.joml.Matrix4dc;
import org.joml.Matrix4fc;

public final class MixinInterfaces {

    public interface ISendsChunkWatchPackets {
        void vs$sendWatchPackets(ServerPlayerEntity player, ChunkPos pos, Packet<?>[] packets);
    }

    public interface ISetMatrix4fFromJOML {
        void vs$setFromJOML(Matrix4dc m);
        void vs$setFromJOML(Matrix4fc m);
    }
}