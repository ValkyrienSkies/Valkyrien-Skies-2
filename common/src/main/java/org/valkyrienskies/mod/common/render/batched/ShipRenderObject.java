package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.compat.sodium.light.VsShipEmitterList;
import org.valkyrienskies.mod.common.config.VSGameConfig;

public final class ShipRenderObject implements AutoCloseable {

    private static final double[] NO_EMITTERS = new double[0];

    public final ClientShip ship;

    private ShipMesh mesh = null;
    private boolean meshEmpty = true;

    // Compiled sections, kept so a change only re-tessellates the sections it touched.
    private final Long2ObjectOpenHashMap<ShipSectionCompiler.CompiledSection> sections =
        new Long2ObjectOpenHashMap<>();
    private int refX, refY, refZ;
    private final boolean[] changedLayers = new boolean[ShipSectionCompiler.LAYER_COUNT];
    private boolean fullRemeshRequested = false;

    private final LongOpenHashSet dirtySections = new LongOpenHashSet();

    private long lastActiveChunkSignature = Long.MIN_VALUE;
    private int lastActiveChunkCount = -1;
    private long lastChunkPollGameTime = Long.MIN_VALUE;
    private boolean built = false;
    private final ShipBiomeTintCache biomeTints = new ShipBiomeTintCache();
    private long lastBiomeTick = Long.MIN_VALUE;

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
        blockEntitiesDirty = true;
        emittersDirty = true;
    }

    /** Like {@link #markSectionDirty} for a light-only change: emitters and block entities stay valid. */
    public void markSectionLightDirty(final int sx, final int sy, final int sz) {
        synchronized (dirtySections) {
            dirtySections.add(SectionPos.asLong(sx, sy, sz));
        }
    }

    public boolean ensureCompiled(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler, final boolean incrementalBudgetAvailable) {

        final long gameTime = level.getGameTime();
        if (built && VSGameConfig.CLIENT.getDynamicShipBiomeTinting() && lastBiomeTick != gameTime) {
            lastBiomeTick = gameTime;
            if (biomeTints.hasChanged(level, ship)) {
                fullRemeshRequested = true;
                blockEntitiesDirty = true;
                emittersDirty = true;
            }
        }
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
        if (!hasDirty && !fullRemeshRequested) {
            return false;
        }
        if (!incrementalBudgetAvailable) {
            return false; // defer; re-mesh on a later frame when budget allows
        }
        if (fullRemeshRequested) {
            fullRemesh(level, dispatcher, compiler);
            synchronized (dirtySections) {
                dirtySections.clear();
            }
        } else {
            final LongOpenHashSet dirty;
            synchronized (dirtySections) {
                dirty = new LongOpenHashSet(dirtySections);
                dirtySections.clear();
            }
            partialRemesh(level, dispatcher, compiler, dirty);
        }
        blockEntitiesDirty = true;
        return true;
    }

    /** Re-tessellate every section of the ship. Needed when its chunks or reference point change. */
    private void fullRemesh(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler) {
        fullRemeshRequested = false;
        closeSections();

        final LongList sectionPositions = new LongArrayList();
        final int[] minChunk = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        ship.getActiveChunksSet().forEach((x, z) -> {
            minChunk[0] = Math.min(minChunk[0], x);
            minChunk[1] = Math.min(minChunk[1], z);
            final LevelChunk chunk = level.getChunk(x, z);
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                final LevelChunkSection sec = chunk.getSection(level.getSectionIndexFromSectionY(y));
                if (!sec.hasOnlyAir()) {
                    sectionPositions.add(SectionPos.asLong(x, y, z));
                }
            }
        });
        if (minChunk[0] != Integer.MAX_VALUE) {
            refX = minChunk[0] << 4;
            refY = level.getMinBuildHeight();
            refZ = minChunk[1] << 4;
        }

        biomeTints.clear();
        Arrays.fill(changedLayers, true);
        if (!sectionPositions.isEmpty()) {
            compiler.compileSections(level, dispatcher, ship, biomeTints, sectionPositions,
                refX, refY, refZ, sections, changedLayers);
        }
        rebuildMesh(compiler);
    }

    /** Re-tessellate only the dirty sections, then rebuild the opaque layers they touched. */
    private void partialRemesh(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final ShipSectionCompiler compiler, final LongOpenHashSet dirty) {
        final LongList sectionPositions = new LongArrayList(dirty.size());
        final var it = dirty.iterator();
        while (it.hasNext()) {
            final long secPos = it.nextLong();
            final int y = SectionPos.y(secPos);
            // Neighbor marks can fall outside the ship's chunks or the world's height.
            if (y < level.getMinSection() || y >= level.getMaxSection()
                || !ship.getActiveChunksSet().contains(SectionPos.x(secPos), SectionPos.z(secPos))) {
                continue;
            }
            sectionPositions.add(secPos);
        }
        if (sectionPositions.isEmpty()) {
            return;
        }
        Arrays.fill(changedLayers, false);
        compiler.compileSections(level, dispatcher, ship, biomeTints, sectionPositions,
            refX, refY, refZ, sections, changedLayers);
        rebuildMesh(compiler);
    }

    /** Merge the changed opaque layers and collect the translucent sections into a new mesh. */
    private void rebuildMesh(final ShipSectionCompiler compiler) {
        final ShipMesh old = this.mesh;
        if (sections.isEmpty()) {
            this.mesh = null;
            this.meshEmpty = true;
            if (old != null) {
                old.close();
            }
            return;
        }

        final VertexBuffer[] opaque = new VertexBuffer[ShipSectionCompiler.LAYER_COUNT];
        final int translucentIndex = ShipSectionMesh.layerIndex(RenderType.translucent());
        for (int i = 0; i < opaque.length; i++) {
            if (i == translucentIndex) {
                continue;
            }
            if (old == null || changedLayers[i]) {
                opaque[i] = compiler.mergeOpaqueLayer(i, sections.values());
            } else {
                // Unchanged layer: move the buffer to the new mesh so closing the old one keeps it.
                opaque[i] = old.opaque[i];
                old.opaque[i] = null;
            }
        }

        final Long2ObjectMap<ShipSectionMesh> translucent = new Long2ObjectOpenHashMap<>();
        for (final var entry : sections.long2ObjectEntrySet()) {
            if (entry.getValue().translucent != null) {
                translucent.put(entry.getLongKey(), entry.getValue().translucent);
            }
        }

        this.mesh = new ShipMesh(opaque, translucent, refX, refY, refZ);
        this.meshEmpty = this.mesh.isEmpty();
        if (old != null) {
            old.close();
        }
    }

    private void closeSections() {
        for (final ShipSectionCompiler.CompiledSection section : sections.values()) {
            section.close();
        }
        sections.clear();
    }

    @Override
    public void close() {
        biomeTints.clear();
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        closeSections();
        meshEmpty = true;
    }
}
