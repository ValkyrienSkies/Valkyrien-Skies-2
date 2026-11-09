package org.valkyrienskies.mod.mixin.mod_compat.immersive_portals;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.mixinducks.client.render.IVSViewAreaMethods;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;

/**
 * Reimplementation of {@link org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer.MixinViewAreaVanilla} for immersive portals
 */
@Mixin(ImmPtlViewArea.class)
public class MixinMyBuiltChunkStorage extends ViewArea implements IVSViewAreaMethods {

    // Maps chunk position to an array of BuiltChunk, indexed by the y value.
    @Unique
    private final Long2ObjectMap<SectionRenderDispatcher.RenderSection[]> vs$shipRenderChunks =
        new Long2ObjectOpenHashMap<>();
    // This creates render chunks
    @Unique
    private SectionRenderDispatcher vs$chunkBuilder;

    public MixinMyBuiltChunkStorage(final SectionRenderDispatcher chunkRenderDispatcher, final Level level, final int i,
        final LevelRenderer levelRenderer) {
        super(chunkRenderDispatcher, level, i, levelRenderer);
    }

    @Override
    public void vs$fillRenderChunkPool() {
        // Immersive Portals owns the lifetime of its view areas; allocate on demand.
    }

    /**
     * This mixin stores the [chunkBuilder] object from the constructor. It is used to create new render chunks.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(final SectionRenderDispatcher chunkBuilder, final Level world, final int viewDistance,
        final LevelRenderer worldRenderer, final CallbackInfo callbackInfo) {

        this.vs$chunkBuilder = chunkBuilder;
    }

    /**
     * This mixin creates render chunks for ship chunks.
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
            // Only mark existing render chunks dirty — don't create new ones.
            // Creation is deferred to vs$getOrCreateShipRenderChunk (called from
            // vs$addShipVisibleChunks) which only creates for non-empty sections.
            final long chunkPosAsLong = ChunkPos.asLong(x, z);
            final SectionRenderDispatcher.RenderSection[] renderChunksArray =
                vs$shipRenderChunks.computeIfAbsent(chunkPosAsLong,
                    k -> new SectionRenderDispatcher.RenderSection[sectionGridSizeY]);

            if (renderChunksArray[yIndex] == null) {
                final SectionRenderDispatcher.RenderSection builtChunk =
                    vs$chunkBuilder.new RenderSection(0, x << 4, y << 4, z << 4);
                renderChunksArray[yIndex] = builtChunk;
            }

            renderChunksArray[yIndex].setDirty(important);

            callbackInfo.cancel();
        }
    }

    /**
     * This mixin allows {@link ViewArea} to return the render chunks for ships.
     */
    @Inject(method = "provideBuiltChunkByChunkPos", at = @At("HEAD"), cancellable = true)
    private void preProvideBuiltChunkByChunkPos(
        final int cx,
        final int cy,
        final int cz,
        final CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> callbackInfoReturnable
    ) {
        final int chunkX = Mth.floorDiv(cx, 16);
        final int chunkY = Mth.floorDiv(cy - level.getMinBuildHeight(), 16);
        final int chunkZ = Mth.floorDiv(cz, 16);

        if (chunkY < 0 || chunkY >= sectionGridSizeY) {
            return; // Weird, but ignore it
        }

        var ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, chunkX, chunkZ);
        if (ship != null && ShipRendererKt.getUsesTerrainChunkRenderer(ship)) {
            final long chunkPosAsLong = ChunkPos.asLong(chunkX, chunkZ);
            final SectionRenderDispatcher.RenderSection[] renderChunksArray = vs$shipRenderChunks.get(chunkPosAsLong);
            if (renderChunksArray == null) {
                callbackInfoReturnable.setReturnValue(null);
                return;
            }
            final SectionRenderDispatcher.RenderSection renderChunk = renderChunksArray[chunkY];
            callbackInfoReturnable.setReturnValue(renderChunk);
        }
    }

    @Override
    public SectionRenderDispatcher.RenderSection vs$getShipRenderSection(final int chunkX, final int sectionY, final int chunkZ) {
        final int yIndex = sectionY - level.getMinSection();
        if (yIndex < 0 || yIndex >= sectionGridSizeY) {
            return null;
        }
        final SectionRenderDispatcher.RenderSection[] arr = vs$shipRenderChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        return arr != null ? arr[yIndex] : null;
    }

    @Override
    public SectionRenderDispatcher.RenderSection vs$getOrCreateShipRenderSection(
        final int chunkX, final int sectionY, final int chunkZ
    ) {
        final int yIndex = sectionY - level.getMinSection();
        if (yIndex < 0 || yIndex >= sectionGridSizeY) {
            return null;
        }
        final long key = ChunkPos.asLong(chunkX, chunkZ);
        final SectionRenderDispatcher.RenderSection[] arr =
            vs$shipRenderChunks.computeIfAbsent(key, k -> new SectionRenderDispatcher.RenderSection[sectionGridSizeY]);
        if (arr[yIndex] == null) {
            arr[yIndex] = vs$chunkBuilder.new RenderSection(0, chunkX << 4, sectionY << 4, chunkZ << 4);
        }
        arr[yIndex].setDirty(true);
        return arr[yIndex];
    }

    @Override
    public void valkyrienskies$unloadChunk(final int chunkX, final int chunkZ) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            final SectionRenderDispatcher.RenderSection[] chunks =
                vs$shipRenderChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
            if (chunks != null) {
                for (final SectionRenderDispatcher.RenderSection chunk : chunks) {
                    if (chunk != null) {
                        chunk.releaseBuffers();
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
        for (final Entry<SectionRenderDispatcher.RenderSection[]> entry : vs$shipRenderChunks.long2ObjectEntrySet()) {
            for (final SectionRenderDispatcher.RenderSection renderChunk : entry.getValue()) {
                if (renderChunk != null) {
                    renderChunk.releaseBuffers();
                }
            }
        }
        vs$shipRenderChunks.clear();
    }
}
