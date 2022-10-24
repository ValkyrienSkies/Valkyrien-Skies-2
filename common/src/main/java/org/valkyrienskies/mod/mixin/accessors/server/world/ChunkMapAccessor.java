package org.valkyrienskies.mod.mixin.accessors.server.world;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("updateChunkTracking")
    void callUpdateChunkTracking(ServerPlayer player, ChunkPos pos, Packet<?>[] packets,
        boolean withinMaxWatchDistance, boolean withinViewDistance);

    @Invoker("getChunks")
    Iterable<ChunkHolder> callGetChunks();

}
