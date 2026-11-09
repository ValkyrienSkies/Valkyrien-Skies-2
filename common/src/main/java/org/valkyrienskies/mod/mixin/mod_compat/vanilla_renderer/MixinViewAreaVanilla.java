package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer;
import org.valkyrienskies.mod.mixin.accessors.client.multiplayer.ClientLevelAccessor;
import org.valkyrienskies.mod.mixinducks.client.render.IVSViewAreaMethods;
import org.valkyrienskies.mod.mixinducks.mod_compat.vanilla_renderer.LevelRendererDuck;

/**
 * The purpose of this mixin is to allow {@link ViewArea} to render ship chunks.
 */
@Mixin(ViewArea.class)
public class MixinViewAreaVanilla implements IVSViewAreaMethods {

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected int sectionGridSizeY;

    // Maps chunk position to an array of BuiltChunk, indexed by the y value.
    @Unique
    private final Long2ObjectMap<SectionRenderDispatcher.RenderSection[]> vs$shipRenderSections =
        new Long2ObjectOpenHashMap<>();
    // This creates render chunks
    @Unique
    private SectionRenderDispatcher vs$chunkBuilder;

    // Pool of pre-allocated RenderSections with GPU buffers already created.
    // Taking from the pool avoids blocking glGenBuffers calls during ship loading.
    @Unique
    private final Deque<SectionRenderDispatcher.RenderSection> vs$renderChunkPool = new ArrayDeque<>(1024);
    @Unique
    private static final int VS$POOL_TARGET_SIZE = 1024;
    @Unique
    private static final int VS$POOL_FILL_PER_FRAME = 50;

    /**
     * This mixin stores the [chunkBuilder] object from the constructor. It is used to create new render chunks.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(final SectionRenderDispatcher chunkBuilder, final Level world, final int viewDistance,
        final LevelRenderer worldRenderer, final CallbackInfo callbackInfo) {

        this.vs$chunkBuilder = chunkBuilder;
    }

    @Override
    public void vs$fillRenderSectionPool() {
        final int toFill = Math.min(VS$POOL_FILL_PER_FRAME, VS$POOL_TARGET_SIZE - vs$renderChunkPool.size());
        for (int i = 0; i < toFill; i++) {
            vs$renderChunkPool.push(vs$chunkBuilder.new RenderSection(0, 0, 0, 0));
        }
    }

    /**
     * Take a RenderSection from the pool and reposition it, or create a new one if pool is empty.
     */
    @Unique
    private SectionRenderDispatcher.RenderSection vs$takeRenderSection(final int blockX, final int blockY, final int blockZ) {
        final SectionRenderDispatcher.RenderSection pooled = vs$renderChunkPool.poll();
        if (pooled != null) {
            // setOrigin also updates the section bounds and neighbor origins in 1.21.
            pooled.setOrigin(blockX, blockY, blockZ);
            return pooled;
        }
        // Pool empty — fall back to fresh allocation
        return vs$chunkBuilder.new RenderSection(0, blockX, blockY, blockZ);
    }

    /**
     * Return a RenderSection to the pool for reuse instead of releasing its GPU buffers.
     */
    @Unique
    private void vs$returnToPool(final SectionRenderDispatcher.RenderSection chunk) {
        if (vs$renderChunkPool.size() < VS$POOL_TARGET_SIZE) {
            vs$renderChunkPool.push(chunk);
        } else {
            chunk.releaseBuffers();
        }
    }

    @Unique
    private void vs$markExistingShipRenderSectionDirty(final int chunkX, final int sectionY, final int chunkZ,
        final boolean important) {
        final int yIndex = sectionY - level.getMinSection();
        if (yIndex < 0 || yIndex >= sectionGridSizeY) {
            return;
        }
        final SectionRenderDispatcher.RenderSection[] renderChunksArray = vs$shipRenderSections.get(ChunkPos.asLong(chunkX, chunkZ));
        if (renderChunksArray != null && renderChunksArray[yIndex] != null) {
            renderChunksArray[yIndex].setDirty(important);
        }
    }

    /**
     * Intercept setDirty for ship chunks. We do NOT create RenderSections here because
     * each RenderSection constructor calls glGenBuffers (blocking GPU allocation).
     * When enableChunkLight fires for ship chunks, it calls setSectionDirtyWithNeighbors
     * for ALL 24 Y sections + neighbors — potentially 168,000 RenderSection allocations
     * for 200 ships, freezing the render thread.
     *
     * Instead, we only mark EXISTING render chunks dirty. New render chunks are created
     * lazily by vs$addShipVisibleChunks, which filters empty sections and is throttled.
     */
    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void preScheduleRebuild(final int x, final int y, final int z, final boolean important,
        final CallbackInfo callbackInfo) {

        final int yIndex = y - level.getMinSection();

        if (yIndex < 0 || yIndex >= sectionGridSizeY) {
            return; // Weird, but just ignore it
        }

        var ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, x, z);
        if (ship != null && ShipRendererKt.getUsesTerrainChunkRenderer(ship)) {
            if (this.level instanceof final ClientLevel clientLevel) {
                final LevelRenderer levelRenderer = ((ClientLevelAccessor) clientLevel).getLevelRenderer();
                if (levelRenderer instanceof final LevelRendererDuck levelRendererDuck) {
                    levelRendererDuck.vs$setShipChunkVisibilityDirty();
                }
            }
            // Only mark existing render chunks dirty — don't create new ones.
            // Creation is deferred to vs$getOrCreateShipRenderSection (called from
            // vs$addShipVisibleChunks) which only creates for non-empty sections.
            vs$markExistingShipRenderSectionDirty(x, y, z, important);
            vs$markExistingShipRenderSectionDirty(x - 1, y, z, important);
            vs$markExistingShipRenderSectionDirty(x + 1, y, z, important);
            vs$markExistingShipRenderSectionDirty(x, y - 1, z, important);
            vs$markExistingShipRenderSectionDirty(x, y + 1, z, important);
            vs$markExistingShipRenderSectionDirty(x, y, z - 1, important);
            vs$markExistingShipRenderSectionDirty(x, y, z + 1, important);

            callbackInfo.cancel();
        } else if (ship != null && ShipRendererKt.getUsesBatchedRenderer(ship)) {
            ShipBatchRenderer.INSTANCE.markSectionDirty(ship.getId(), x, y, z);
            callbackInfo.cancel();
        }
    }

    /**
     * This mixin allows {@link ViewArea} to return the render chunks for ships.
     */
    @Inject(method = "getRenderSectionAt", at = @At("HEAD"), cancellable = true)
    private void preGetRenderedChunk(final BlockPos pos,
        final CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> callbackInfoReturnable) {
        final int chunkX = Mth.floorDiv(pos.getX(), 16);
        final int chunkY = Mth.floorDiv(pos.getY() - level.getMinBuildHeight(), 16);
        final int chunkZ = Mth.floorDiv(pos.getZ(), 16);

        if (chunkY < 0 || chunkY >= sectionGridSizeY) {
            return; // Weird, but ignore it
        }

        var ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, chunkX, chunkZ);
        if (ship != null && ShipRendererKt.getUsesTerrainChunkRenderer(ship)) {
            final long chunkPosAsLong = ChunkPos.asLong(chunkX, chunkZ);
            final SectionRenderDispatcher.RenderSection[] renderChunksArray = vs$shipRenderSections.get(chunkPosAsLong);
            if (renderChunksArray == null) {
                callbackInfoReturnable.setReturnValue(null);
                return;
            }
            final SectionRenderDispatcher.RenderSection renderChunk = renderChunksArray[chunkY];
            callbackInfoReturnable.setReturnValue(renderChunk);
        } else if (ship != null && ShipRendererKt.getUsesBatchedRenderer(ship)) {
            callbackInfoReturnable.setReturnValue(null);
        }
    }

    @Override
    public SectionRenderDispatcher.RenderSection vs$getShipRenderSection(final int chunkX, final int sectionY, final int chunkZ) {
        final int yIndex = sectionY - level.getMinSection();
        if (yIndex < 0 || yIndex >= sectionGridSizeY) return null;
        final SectionRenderDispatcher.RenderSection[] arr = vs$shipRenderSections.get(ChunkPos.asLong(chunkX, chunkZ));
        return arr != null ? arr[yIndex] : null;
    }

    @Override
    public SectionRenderDispatcher.RenderSection vs$getOrCreateShipRenderSection(final int chunkX, final int sectionY, final int chunkZ) {
        final int yIndex = sectionY - level.getMinSection();
        if (yIndex < 0 || yIndex >= sectionGridSizeY) return null;
        final long key = ChunkPos.asLong(chunkX, chunkZ);
        final SectionRenderDispatcher.RenderSection[] arr =
            vs$shipRenderSections.computeIfAbsent(key, k -> new SectionRenderDispatcher.RenderSection[sectionGridSizeY]);
        if (arr[yIndex] == null) {
            arr[yIndex] = vs$takeRenderSection(chunkX << 4, sectionY << 4, chunkZ << 4);
        }
        arr[yIndex].setDirty(true);
        return arr[yIndex];
    }

    @Override
    public void valkyrienskies$unloadChunk(final int chunkX, final int chunkZ) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            final SectionRenderDispatcher.RenderSection[] chunks =
                vs$shipRenderSections.remove(ChunkPos.asLong(chunkX, chunkZ));
            if (chunks != null) {
                for (final SectionRenderDispatcher.RenderSection chunk : chunks) {
                    if (chunk != null) {
                        vs$returnToPool(chunk);
                    }
                }
            }
        }
    }

    /**
     * Clear VS ship render chunks so that we don't leak memory
     */
    @Inject(method = "releaseAllBuffers", at = @At("HEAD"))
    private void postReleaseAllBuffers(final CallbackInfo ci) {
        for (final Entry<RenderSection[]> entry : vs$shipRenderSections.long2ObjectEntrySet()) {
            for (final SectionRenderDispatcher.RenderSection renderChunk : entry.getValue()) {
                if (renderChunk != null) {
                    renderChunk.releaseBuffers();
                }
            }
        }
        vs$shipRenderSections.clear();
        // Also release pooled chunks
        for (final SectionRenderDispatcher.RenderSection pooled : vs$renderChunkPool) {
            pooled.releaseBuffers();
        }
        vs$renderChunkPool.clear();
    }
}
