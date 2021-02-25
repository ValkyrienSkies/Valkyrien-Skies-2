package org.valkyrienskies.mod.mixin.client.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * The purpose of this mixin is to allow {@link BuiltChunkStorage} to render ship chunks.
 */
@Mixin(BuiltChunkStorage.class)
public class MixinBuiltChunkStorage {

    @Shadow
    @Final
    protected World world;

    @Shadow
    protected int sizeY;

    // Maps chunk position to an array of BuiltChunk, indexed by the y value.
    private final Long2ObjectMap<ChunkBuilder.BuiltChunk[]> vs$shipRenderChunks = new Long2ObjectOpenHashMap<>();
    // This creates render chunks
    private ChunkBuilder vs$chunkBuilder;

    /**
     * This mixin stores the [chunkBuilder] object from the constructor. It is used to create new render chunks.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(ChunkBuilder chunkBuilder, World world, int viewDistance, WorldRenderer worldRenderer, CallbackInfo callbackInfo) {
        this.vs$chunkBuilder = chunkBuilder;
    }

    /**
     * This mixin creates render chunks for ship chunks.
     */
    @Inject(method = "scheduleRebuild", at = @At("HEAD"), cancellable = true)
    private void preScheduleRebuild(int x, int y, int z, boolean important, CallbackInfo callbackInfo) {
        if (y < 0 || y >= sizeY) return; // Weird, but just ignore it
        if (VSGameUtilsKt.getShipObjectWorld(world).getChunkAllocator().isChunkInShipyard(x, z)) {
            final long chunkPosAsLong = ChunkPos.toLong(x, z);
            final ChunkBuilder.BuiltChunk[] renderChunksArray =
                    vs$shipRenderChunks.computeIfAbsent(chunkPosAsLong, k -> new ChunkBuilder.BuiltChunk[sizeY]);

            if (renderChunksArray[y] == null) {
                final ChunkBuilder.BuiltChunk builtChunk = vs$chunkBuilder.new BuiltChunk();
                builtChunk.setOrigin(x << 4, y << 4, z << 4);
                renderChunksArray[y] = builtChunk;
            }

            renderChunksArray[y].scheduleRebuild(important);

            callbackInfo.cancel();
        }
    }

    /**
     * This mixin allows {@link BuiltChunkStorage} to return the render chunks for ships.
     */
    @Inject(method = "getRenderedChunk", at = @At("HEAD"), cancellable = true)
    private void preGetRenderedChunk(BlockPos pos, CallbackInfoReturnable<ChunkBuilder.BuiltChunk> callbackInfoReturnable) {
        final int chunkX = MathHelper.floorDiv(pos.getX(), 16);
        final int chunkY = MathHelper.floorDiv(pos.getY(), 16);
        final int chunkZ = MathHelper.floorDiv(pos.getZ(), 16);

        if (chunkY < 0 || chunkY >= sizeY) return; // Weird, but ignore it

        if (VSGameUtilsKt.getShipObjectWorld(world).getChunkAllocator().isChunkInShipyard(chunkX, chunkZ)) {
            final long chunkPosAsLong = ChunkPos.toLong(chunkX, chunkZ);
            final ChunkBuilder.BuiltChunk[] renderChunksArray = vs$shipRenderChunks.get(chunkPosAsLong);
            if (renderChunksArray == null) {
                callbackInfoReturnable.setReturnValue(null);
                return;
            }
            final ChunkBuilder.BuiltChunk renderChunk = renderChunksArray[chunkY];
            callbackInfoReturnable.setReturnValue(renderChunk);
        }
    }
}
