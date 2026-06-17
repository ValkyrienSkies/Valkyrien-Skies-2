package org.valkyrienskies.mod.mixinducks.client.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface ClientChunkCacheDuck {
    Long2ObjectMap<LevelChunk> vs$getShipChunks();
    void vs$removeShip(ClientShip ship);
    void vs$drainShipChunkUnloadQueue();

    /**
     * Re-run the ship-chunk lighting pipeline for the chunk at ({@code chunkX},
     * {@code chunkZ}). Safe to call after vanilla's {@code applyLightData}
     * clobbered the light engine state with packet data from a freshly-spawned
     * ship (which contains zeros, because server-side light init is skipped
     * during batch assembly).
     *
     * <p>No-op if the chunk is not in {@code vs$shipChunks} or is fully empty.
     * Caller is responsible for running {@code runLightUpdates()} afterwards
     * — during bulk drain that happens in {@code drainDeferredBatch}; outside
     * of that, call it yourself.
     */
    void vs$relightShipChunk(int chunkX, int chunkZ);

    /**
     * Variant of {@link #vs$relightShipChunk(int, int)} that controls whether
     * the air-cell {@code checkBlock} sweep runs. The sweep is required for
     * INCREMENTAL block updates (placing a block that encloses a previously
     * open hollow — the engine must re-evaluate all air cells because the
     * decrease propagation doesn't reach them otherwise). It is NOT required
     * for INITIAL chunk load (the packet carries a consistent block+light
     * state; there's nothing to re-derive).
     *
     * <p>Cost difference: the sweep iterates 4096 cells per non-empty
     * section in the 3×3 neighborhood and calls {@code checkBlock} on each.
     * For 1000-ship spawn (5000 applyLightData packets × 9 neighborhood
     * chunks), that's ~184 million {@code checkBlock} calls. Skipping the
     * sweep on initial load turns the 1000-ship drain from ~18 seconds
     * into something much faster.
     *
     * @param sweepAirCells true = full re-evaluation including air cells
     *                      (for block updates), false = non-air only (for
     *                      initial chunk load / light packet apply)
     */
    void vs$relightShipChunk(int chunkX, int chunkZ, boolean sweepAirCells);

    /**
     * Synchronously pre-enable sky lighting on the 3x3 shipyard-chunk
     * neighborhood of ({@code chunkX}, {@code chunkZ}). Call this at the
     * TAIL of {@code applyLightData} — BEFORE the queueLightUpdate runnable
     * invokes {@code enableChunkLight} on the arriving ship chunk — so
     * that the empty-neighbor initializeSection calls triggered by
     * {@code enableChunkLight}'s neighborCount bumps start with a sky=15
     * DataLayer instead of zero. See MixinClientPacketListener's
     * {@code applyLightData} TAIL hook for the full timing rationale.
     */
    void vs$preEnableShipChunkNeighborhood(int chunkX, int chunkZ);

    /**
     * Has the ship chunk at ({@code chunkX}, {@code chunkZ}) received its
     * first {@code applyLightData}+relight pass yet? Used to gate the
     * HEAD-cancel in {@code MixinClientPacketListener} — once a ship chunk
     * is lit, all subsequent {@code ClientboundLightUpdatePacket} broadcasts
     * carry server-uninitialised zeros (shipyard chunks skip the server's
     * {@code initializeLight}/{@code light} stages) and would clobber our
     * correct DataLayers if {@code applyLightData} ran.
     *
     * <p>Cleared on chunk unload so a re-loaded ship chunk starts fresh.
     */
    boolean vs$hasBeenLitOnce(int chunkX, int chunkZ);

    /**
     * Record that the ship chunk at ({@code chunkX}, {@code chunkZ}) has
     * had its first successful client-side relight. Call at the TAIL of
     * {@code applyLightData} AFTER the relight returns without exception.
     */
    void vs$markLitOnce(int chunkX, int chunkZ);

    interface StorageDuck {
        void vs$incChunkCount();
        void vs$decChunkCount();
    }
}
