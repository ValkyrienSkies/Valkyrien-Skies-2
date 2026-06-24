package org.valkyrienskies.mod.common.render.batched;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.compat.sodium.light.VsShipEmitterList;

public final class ShipRenderObject implements AutoCloseable {

    private static final double[] NO_EMITTERS = new double[0];

    public final ClientShip ship;

    private ShipMesh mesh = null;
    private boolean meshEmpty = true;

    private final LongOpenHashSet dirtySections = new LongOpenHashSet();

    private long lastActiveChunkSignature = Long.MIN_VALUE;
    private int lastActiveChunkCount = -1;
    private long lastChunkPollGameTime = Long.MIN_VALUE;
    private boolean built = false;

    private static final int RELIGHT_SETTLE_QUIET_TICKS = 3;
    private static final long RELIGHT_SETTLE_MAX_DELAY_TICKS = 200L; // ~10s cap for slow mass-spawn propagation
    private boolean relightRemeshArmed = false;
    private int relightQuietTicks = 0;
    private long relightLastTick = Long.MIN_VALUE;
    private long relightDeadlineTick = Long.MIN_VALUE;

    private double[] cachedShipyardEmitters = NO_EMITTERS;
    private volatile boolean emittersDirty = true;

    private final List<BlockEntity> blockEntities = new ArrayList<>();
    private boolean blockEntitiesDirty = true;

    public ShipRenderObject(final ClientShip ship) {
        this.ship = ship;
    }

    public ShipMesh getMesh() {
        return mesh;
    }

    public boolean isEmpty() {
        return meshEmpty;
    }

    public double[] getShipyardEmitters(final ClientLevel level) {
        if (emittersDirty) {
            if (ship.getShipAABB() == null) {
                return NO_EMITTERS;
            }
            emittersDirty = false;
            cachedShipyardEmitters = VsShipEmitterList.scanShipEmitters(level, ship);
        }
        return cachedShipyardEmitters;
    }

    public List<BlockEntity> getBlockEntities(final ClientLevel level) {
        if (blockEntitiesDirty) {
            blockEntities.clear();
            ship.getActiveChunksSet().forEach((x, z) -> {
                final LevelChunk chunk = level.getChunk(x, z);
                blockEntities.addAll(chunk.getBlockEntities().values());
            });
            blockEntitiesDirty = false;
        }
        return blockEntities;
    }

    public void markSectionDirty(final int sx, final int sy, final int sz) {
        synchronized (dirtySections) {
            dirtySections.add(SectionPos.asLong(sx, sy, sz));
        }
        emittersDirty = true;
    }

    public boolean ensureCompiled(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler, final boolean incrementalBudgetAvailable) {

        final long gameTime = level.getGameTime();
        boolean structuralChange = false;
        if (!built || gameTime != lastChunkPollGameTime) {
            lastChunkPollGameTime = gameTime;
            final long[] signature = {0L};
            final int[] count = {0};
            ship.getActiveChunksSet().forEach((x, z) -> {
                final long key = ChunkPos.asLong(x, z);
                count[0]++;
                signature[0] += key * 0x9E3779B97F4A7C15L;
                signature[0] ^= Long.rotateLeft(key, 32);
            });
            structuralChange =
                !built || count[0] != lastActiveChunkCount || signature[0] != lastActiveChunkSignature;
            if (structuralChange) {
                lastActiveChunkSignature = signature[0];
                lastActiveChunkCount = count[0];
            }
        }

        if (structuralChange) {
            fullRemesh(level, dispatcher, compiler);
            synchronized (dirtySections) {
                dirtySections.clear();
            }
            built = true;
            blockEntitiesDirty = true;
            emittersDirty = true;
            relightRemeshArmed = true;
            relightQuietTicks = 0;
            relightDeadlineTick = gameTime + RELIGHT_SETTLE_MAX_DELAY_TICKS;
            return false;
        }

        if (relightRemeshArmed) {
            if (gameTime != relightLastTick) {
                relightLastTick = gameTime;
                if (level.getLightEngine().hasLightWork()) {
                    relightQuietTicks = 0;
                } else {
                    relightQuietTicks++;
                }
            }
            if (relightQuietTicks >= RELIGHT_SETTLE_QUIET_TICKS || gameTime >= relightDeadlineTick) {
                if (!incrementalBudgetAvailable) {
                    return false; // defer to a frame with re-mesh budget
                }
                fullRemesh(level, dispatcher, compiler);
                synchronized (dirtySections) {
                    dirtySections.clear();
                }
                relightRemeshArmed = false;
                blockEntitiesDirty = true;
                emittersDirty = true;
                return true;
            }
        }

        final boolean hasDirty;
        synchronized (dirtySections) {
            hasDirty = !dirtySections.isEmpty();
        }
        if (!hasDirty) {
            return false;
        }
        if (!incrementalBudgetAvailable) {
            return false; // defer; re-mesh on a later frame when budget allows
        }
        fullRemesh(level, dispatcher, compiler);
        synchronized (dirtySections) {
            dirtySections.clear();
        }
        blockEntitiesDirty = true;
        return true;
    }

    private void fullRemesh(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler) {
        final LongList sectionPositions = new LongArrayList();
        final int[] minOrigin = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        ship.getActiveChunksSet().forEach((x, z) -> {
            final LevelChunk chunk = level.getChunk(x, z);
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                final LevelChunkSection sec = chunk.getSection(level.getSectionIndexFromSectionY(y));
                if (!sec.hasOnlyAir()) {
                    sectionPositions.add(SectionPos.asLong(x, y, z));
                    minOrigin[0] = Math.min(minOrigin[0], x << 4);
                    minOrigin[1] = Math.min(minOrigin[1], y << 4);
                    minOrigin[2] = Math.min(minOrigin[2], z << 4);
                }
            }
        });

        final ShipMesh newMesh = sectionPositions.isEmpty()
            ? null
            : compiler.compileShip(level, dispatcher, sectionPositions, minOrigin[0], minOrigin[1], minOrigin[2]);

        final ShipMesh old = this.mesh;
        this.mesh = newMesh;
        this.meshEmpty = newMesh == null || newMesh.isEmpty();
        if (old != null) {
            old.close();
        }
    }

    @Override
    public void close() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        meshEmpty = true;
    }
}
