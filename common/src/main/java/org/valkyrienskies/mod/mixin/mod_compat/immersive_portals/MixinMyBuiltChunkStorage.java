package org.valkyrienskies.mod.mixin.mod_compat.immersive_portals;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk;
import net.minecraft.core.BlockPos;
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
import org.valkyrienskies.mod.mixinducks.client.render.IVSViewAreaMethods;
import qouteall.imm_ptl.core.render.MyBuiltChunkStorage;

/**
 * Reimplementation of {@link org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer.MixinViewAreaVanilla} for immersive portals
 */
@Mixin(MyBuiltChunkStorage.class)
public class MixinMyBuiltChunkStorage extends ViewArea implements IVSViewAreaMethods {

    // Maps chunk position to an array of BuiltChunk, indexed by the y value.
    @Unique
    private final Long2ObjectMap<RenderChunk[]> vs$shipRenderChunks =
        new Long2ObjectOpenHashMap<>();
    // This creates render chunks
    @Unique
    private ChunkRenderDispatcher vs$chunkBuilder;

    public MixinMyBuiltChunkStorage(final ChunkRenderDispatcher chunkRenderDispatcher, final Level level, final int i,
        final LevelRenderer levelRenderer) {
        super(chunkRenderDispatcher, level, i, levelRenderer);
    }

    /**
     * This mixin stores the [chunkBuilder] object from the constructor. It is used to create new render chunks.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(final ChunkRenderDispatcher chunkBuilder, final Level world, final int viewDistance,
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

        if (yIndex < 0 || yIndex >= chunkGridSizeY) {
            return; // Weird, but just ignore it
        }

        if (VSGameUtilsKt.isChunkInShipyard(level, x, z)) {
            final long chunkPosAsLong = ChunkPos.asLong(x, z);
            final ChunkRenderDispatcher.RenderChunk[] renderChunksArray =
                vs$shipRenderChunks.computeIfAbsent(chunkPosAsLong,
                    k -> new ChunkRenderDispatcher.RenderChunk[chunkGridSizeY]);

            if (renderChunksArray[yIndex] == null) {
                final ChunkRenderDispatcher.RenderChunk builtChunk =
                    vs$chunkBuilder.new RenderChunk(0, x << 4, y << 4, z << 4);
                renderChunksArray[yIndex] = builtChunk;
            }

            renderChunksArray[yIndex].setDirty(important);

            callbackInfo.cancel();
        }
    }

    /**
     * This mixin allows {@link ViewArea} to return the render chunks for ships.
     */
    @Inject(method = "getRenderChunkAt", at = @At("HEAD"), cancellable = true)
    private void preGetRenderedChunk(final BlockPos pos,
        final CallbackInfoReturnable<RenderChunk> callbackInfoReturnable) {
        final int chunkX = Mth.floorDiv(pos.getX(), 16);
        final int chunkY = Mth.floorDiv(pos.getY() - level.getMinBuildHeight(), 16);
        final int chunkZ = Mth.floorDiv(pos.getZ(), 16);

        if (chunkY < 0 || chunkY >= chunkGridSizeY) {
            return; // Weird, but ignore it
        }

        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            final long chunkPosAsLong = ChunkPos.asLong(chunkX, chunkZ);
            final ChunkRenderDispatcher.RenderChunk[] renderChunksArray = vs$shipRenderChunks.get(chunkPosAsLong);
            if (renderChunksArray == null) {
                callbackInfoReturnable.setReturnValue(null);
                return;
            }
            final ChunkRenderDispatcher.RenderChunk renderChunk = renderChunksArray[chunkY];
            callbackInfoReturnable.setReturnValue(renderChunk);
        }
    }

    @Override
    public void unloadChunk(final int chunkX, final int chunkZ) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            final ChunkRenderDispatcher.RenderChunk[] chunks =
                vs$shipRenderChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
            if (chunks != null) {
                for (final ChunkRenderDispatcher.RenderChunk chunk : chunks) {
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
        for (final Entry<RenderChunk[]> entry : vs$shipRenderChunks.long2ObjectEntrySet()) {
            for (final ChunkRenderDispatcher.RenderChunk renderChunk : entry.getValue()) {
                if (renderChunk != null) {
                    renderChunk.releaseBuffers();
                }
            }
        }
        vs$shipRenderChunks.clear();
    }
}
