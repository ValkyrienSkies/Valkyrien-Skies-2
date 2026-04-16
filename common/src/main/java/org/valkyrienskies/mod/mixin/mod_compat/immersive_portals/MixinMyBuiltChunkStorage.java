package org.valkyrienskies.mod.mixin.mod_compat.immersive_portals;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRenderer;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.mixin.accessors.client.render.chunk.RenderChunkAccessor;
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

    // Pool of pre-allocated RenderChunks with GPU buffers already created.
    // Taking from the pool avoids blocking glGenBuffers calls during ship loading.
    @Unique
    private final Deque<RenderChunk> vs$renderChunkPool = new ArrayDeque<>(1024);
    @Unique
    private static final int VS$POOL_TARGET_SIZE = 1024;
    @Unique
    private static final int VS$POOL_FILL_PER_FRAME = 50;

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

        var ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, x, z);
        if (ship != null && ShipRendererKt.getShipRenderer(ship) == ShipRenderer.VANILLA) {
            // Only mark existing render chunks dirty — don't create new ones.
            // Creation is deferred to vs$getOrCreateShipRenderChunk (called from
            // vs$addShipVisibleChunks) which only creates for non-empty sections.
            final long chunkPosAsLong = ChunkPos.asLong(x, z);
            final ChunkRenderDispatcher.RenderChunk[] renderChunksArray = vs$shipRenderChunks.get(chunkPosAsLong);
            if (renderChunksArray != null && renderChunksArray[yIndex] != null) {
                renderChunksArray[yIndex].setDirty(important);
            }

            callbackInfo.cancel();
        }
    }

    /**
     * This mixin allows {@link ViewArea} to return the render chunks for ships.
     */
    @Inject(method = "getRenderChunkAt", at = @At("HEAD"), cancellable = true)
    private void preGetRenderedChunk(final BlockPos pos,
        final CallbackInfoReturnable<ChunkRenderDispatcher.RenderChunk> callbackInfoReturnable) {
        final int chunkX = Mth.floorDiv(pos.getX(), 16);
        final int chunkY = Mth.floorDiv(pos.getY() - level.getMinBuildHeight(), 16);
        final int chunkZ = Mth.floorDiv(pos.getZ(), 16);

        if (chunkY < 0 || chunkY >= chunkGridSizeY) {
            return; // Weird, but ignore it
        }

        var ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, chunkX, chunkZ);
        if (ship != null && ShipRendererKt.getShipRenderer(ship) == ShipRenderer.VANILLA) {
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
                        vs$returnToPool(chunk);
                    }
                }
            }
        }
    }

    @Override
    public ChunkRenderDispatcher.RenderChunk vs$getShipRenderChunk(final int chunkX, final int sectionY, final int chunkZ) {
        final int yIndex = sectionY - level.getMinSection();
        if (yIndex < 0 || yIndex >= chunkGridSizeY) return null;
        final ChunkRenderDispatcher.RenderChunk[] arr = vs$shipRenderChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        return arr != null ? arr[yIndex] : null;
    }

    @Override
    public ChunkRenderDispatcher.RenderChunk vs$getOrCreateShipRenderChunk(final int chunkX, final int sectionY, final int chunkZ) {
        final int yIndex = sectionY - level.getMinSection();
        if (yIndex < 0 || yIndex >= chunkGridSizeY) return null;
        final long key = ChunkPos.asLong(chunkX, chunkZ);
        final ChunkRenderDispatcher.RenderChunk[] arr =
            vs$shipRenderChunks.computeIfAbsent(key, k -> new ChunkRenderDispatcher.RenderChunk[chunkGridSizeY]);
        if (arr[yIndex] == null) {
            arr[yIndex] = vs$takeRenderChunk(chunkX << 4, sectionY << 4, chunkZ << 4);
        }
        arr[yIndex].setDirty(true);
        return arr[yIndex];
    }

    @Override
    public void vs$fillRenderChunkPool() {
        final int toFill = Math.min(VS$POOL_FILL_PER_FRAME, VS$POOL_TARGET_SIZE - vs$renderChunkPool.size());
        for (int i = 0; i < toFill; i++) {
            vs$renderChunkPool.push(vs$chunkBuilder.new RenderChunk(0, 0, 0, 0));
        }
    }

    /**
     * Take a RenderChunk from the pool and reposition it, or create a new one if pool is empty.
     */
    @Unique
    private ChunkRenderDispatcher.RenderChunk vs$takeRenderChunk(final int blockX, final int blockY, final int blockZ) {
        final ChunkRenderDispatcher.RenderChunk pooled = vs$renderChunkPool.poll();
        if (pooled != null) {
            // Reposition the pooled chunk — avoids glGenBuffers
            ((BlockPos.MutableBlockPos) pooled.getOrigin()).set(blockX, blockY, blockZ);
            ((RenderChunkAccessor) pooled).vs$setBb(
                new AABB(blockX, blockY, blockZ, blockX + 16, blockY + 16, blockZ + 16));
            return pooled;
        }
        // Pool empty — fall back to fresh allocation
        return vs$chunkBuilder.new RenderChunk(0, blockX, blockY, blockZ);
    }

    /**
     * Return a RenderChunk to the pool for reuse instead of releasing its GPU buffers.
     */
    @Unique
    private void vs$returnToPool(final ChunkRenderDispatcher.RenderChunk chunk) {
        if (vs$renderChunkPool.size() < VS$POOL_TARGET_SIZE) {
            vs$renderChunkPool.push(chunk);
        } else {
            chunk.releaseBuffers();
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
        // Also release pooled chunks
        for (final ChunkRenderDispatcher.RenderChunk pooled : vs$renderChunkPool) {
            pooled.releaseBuffers();
        }
        vs$renderChunkPool.clear();
    }
}
