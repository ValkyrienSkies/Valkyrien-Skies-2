package org.valkyrienskies.mod.mixin.accessors.server.level;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.BooleanSupplier;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkMap.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
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

    @Invoker("save")
    boolean callSave(ChunkAccess chunkAccess);

    @Accessor("toDrop")
    LongSet getToDrop();

    @Invoker("processUnloads")
    void callProcessUnloads(BooleanSupplier booleanSupplier);

    @Accessor("poiManager")
    PoiManager getPoiManager();

    @Invoker("markPosition")
    byte callMarkPosition(ChunkPos chunkPos, ChunkStatus.ChunkType chunkType);

    @Accessor("distanceManager")
    DistanceManager getDistanceManager();
}
