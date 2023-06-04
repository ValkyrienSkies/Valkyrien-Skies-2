package org.valkyrienskies.mod.mixin.accessors.server.world;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("updateChunkTracking")
    void callUpdateChunkTracking(ServerPlayer player, ChunkPos pos,
        MutableObject<ClientboundLevelChunkWithLightPacket> packets,
        boolean withinMaxWatchDistance, boolean withinViewDistance);

    @Invoker("getChunks")
    Iterable<ChunkHolder> callGetChunks();

    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder callGetVisibleChunkIfPresent(long l);
}
