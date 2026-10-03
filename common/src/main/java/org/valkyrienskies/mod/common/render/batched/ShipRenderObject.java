package org.valkyrienskies.mod.common.render.batched;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

public final class ShipRenderObject implements AutoCloseable {
    public final ClientShip ship;
    private final Long2ObjectMap<ShipMesh> meshes = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirtyBatches = new LongLinkedOpenHashSet();
    private final Long2ObjectMap<LevelChunk> knownChunks = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet activeChunks = new LongOpenHashSet();
    private long lastChunkPollGameTime = Long.MIN_VALUE;
    private final List<BlockEntity> blockEntities = new ArrayList<>();
    private volatile boolean blockEntitiesDirty = true;

    public ShipRenderObject(final ClientShip ship) {
        this.ship = ship;
    }

    Collection<ShipMesh> getMeshes() {
        return meshes.values();
    }

    public boolean isEmpty() {
        for (final ShipMesh mesh : meshes.values()) {
            if (!mesh.isEmpty()) return false;
        }
        return true;
    }

    private static LevelChunk loadedChunk(final ClientLevel level, final long key) {
        // getChunk() returns empty shipyard placeholders while packets are in flight.
        // Never replace a valid mesh with one compiled from such a placeholder.
        return ((ClientChunkCacheDuck) level.getChunkSource()).vs$getShipChunks().get(key);
    }

    public List<BlockEntity> getBlockEntities(final ClientLevel level) {
        if (blockEntitiesDirty) {
            blockEntitiesDirty = false;
            blockEntities.clear();
            for (final long key : activeChunks) {
                final LevelChunk chunk = loadedChunk(level, key);
                if (chunk != null) blockEntities.addAll(chunk.getBlockEntities().values());
            }
        }
        return blockEntities;
    }

    public void markSectionDirty(final int sx, final int sy, final int sz) {
        synchronized (dirtyBatches) {
            dirtyBatches.add(ShipMeshBatches.key(sx, sy, sz));
        }
        blockEntitiesDirty = true;
    }

    public void pollChunks(final ClientLevel level) {
        if (lastChunkPollGameTime == level.getGameTime()) return;
        lastChunkPollGameTime = level.getGameTime();
        activeChunks.clear();
        ship.getActiveChunksSet().forEach((x, z) -> {
            final long key = ChunkPos.asLong(x, z);
            activeChunks.add(key);
            final LevelChunk chunk = loadedChunk(level, key);
            if (chunk != null && knownChunks.get(key) != chunk) {
                knownChunks.put(key, chunk);
                markColumnDirty(level, x, z);
            }
        });
        final var it = knownChunks.long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            final var entry = it.next();
            if (!activeChunks.contains(entry.getLongKey())) {
                markColumnDirty(level, ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()));
                it.remove();
            }
        }
    }

    void markColumnDirty(final ClientLevel level, final int x, final int z) {
        // Include adjacent columns: newly loaded or removed blocks change boundary faces and AO.
        final int first = Math.floorDiv(level.getMinSection(), ShipMeshBatches.SECTIONS_PER_AXIS)
            * ShipMeshBatches.SECTIONS_PER_AXIS;
        for (int sy = first; sy < level.getMaxSection(); sy += ShipMeshBatches.SECTIONS_PER_AXIS) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    markSectionDirty(x + dx, sy, z + dz);
                }
            }
        }
    }

    /** Compile at most eight sections. The caller shares a time/count budget across all ships. */
    boolean compileNextBatch(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler, final long deadline) {
        final int attempts;
        synchronized (dirtyBatches) {
            attempts = dirtyBatches.size();
        }
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempt > 0 && System.nanoTime() >= deadline) return false;
            final long key;
            synchronized (dirtyBatches) {
                if (dirtyBatches.isEmpty()) return false;
                key = dirtyBatches.removeFirstLong();
            }
            final int sx = SectionPos.x(key) * ShipMeshBatches.SECTIONS_PER_AXIS;
            final int sy = SectionPos.y(key) * ShipMeshBatches.SECTIONS_PER_AXIS;
            final int sz = SectionPos.z(key) * ShipMeshBatches.SECTIONS_PER_AXIS;
            final LongArrayList sections = new LongArrayList(8);
            boolean ready = true;
            for (int dx = 0; dx < ShipMeshBatches.SECTIONS_PER_AXIS; dx++) {
                for (int dz = 0; dz < ShipMeshBatches.SECTIONS_PER_AXIS; dz++) {
                    final long chunkKey = ChunkPos.asLong(sx + dx, sz + dz);
                    if (!activeChunks.contains(chunkKey)) continue;
                    final LevelChunk chunk = loadedChunk(level, chunkKey);
                    if (chunk == null) {
                        ready = false;
                        continue;
                    }
                    for (int dy = 0; dy < ShipMeshBatches.SECTIONS_PER_AXIS; dy++) {
                        final int sectionY = sy + dy;
                        if (sectionY >= level.getMinSection() && sectionY < level.getMaxSection()
                            && !chunk.getSection(level.getSectionIndexFromSectionY(sectionY)).hasOnlyAir()) {
                            sections.add(SectionPos.asLong(sx + dx, sectionY, sz + dz));
                        }
                    }
                }
            }
            if (!ready) {
                synchronized (dirtyBatches) {
                    dirtyBatches.add(key); // Retry after chunk arrival; retain the old geometry meanwhile.
                }
                continue;
            }
            if (sections.isEmpty() && !meshes.containsKey(key)) continue;
            final ShipMesh replacement = sections.isEmpty() ? null
                : compiler.compileShip(level, dispatcher, sections, sx << 4, sy << 4, sz << 4);
            final ShipMesh old = replacement == null ? meshes.remove(key) : meshes.put(key, replacement);
            if (old != null) old.close();
            // Notifications received during compilation remain in dirtyBatches.
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        for (final ShipMesh mesh : meshes.values()) mesh.close();
        meshes.clear();
        synchronized (dirtyBatches) {
            dirtyBatches.clear();
        }
        knownChunks.clear();
        activeChunks.clear();
        blockEntities.clear();
    }
}
