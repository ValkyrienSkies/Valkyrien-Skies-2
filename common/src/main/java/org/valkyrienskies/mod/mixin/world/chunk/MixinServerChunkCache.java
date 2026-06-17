package org.valkyrienskies.mod.mixin.world.chunk;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.world.VSTicketType;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor;
import org.valkyrienskies.mod.mixin.accessors.server.level.DistanceManagerAccessor;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCache {

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    ServerLevel level;

    /**
     * Allow shipyard chunks loaded only through SHIP_CHUNK to pass position-ticking checks.
     *
     * This is for transient FULL-only shipyard chunk loads and for active ship chunks when
     * the experimental radius-zero ship chunk ticket mode is enabled.
     *
     * Vanilla's isPositionTicking requires level â‰¤ 32, which means:
     * - Scheduled ticks (buttons, repeaters) don't process
     * - Block entities (furnaces, hoppers) don't tick
     *
     * This mixin checks if the chunk is in the shipyard and loaded at FULL status.
     */
    @Inject(method = "isPositionTicking", at = @At("HEAD"), cancellable = true)
    private void vs$allowShipyardTicking(long pos, CallbackInfoReturnable<Boolean> cir) {
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            // Check if the chunk is actually loaded (FULL status)
            if (((ServerChunkCache) (Object) this).getChunkNow(chunkX, chunkZ) != null) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Vanilla's tickChunks loop only calls ServerLevel.tickChunk() for chunks close enough
     * to a player, or on Forge, chunks with force-tick tickets. Radius-zero SHIP_CHUNK
     * tickets intentionally stay at FULL status and do not create the vanilla forced-ticket
     * ring, so run the block/random tick pass here for active ship chunks vanilla skipped.
     */
    @Inject(method = "tickChunks", at = @At("TAIL"))
    private void vs$tickRadiusZeroShipChunks(final org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!VSGameConfig.SERVER.getPerformance().getUseRadiusZeroShipChunkTickets()) return;

        final int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        for (final ChunkHolder chunkHolder : ((ChunkMapAccessor) chunkMap).callGetChunks()) {
            final LevelChunk chunk = chunkHolder.getTickingChunk();
            if (chunk == null) continue;

            final ChunkPos pos = chunk.getPos();
            if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) continue;
            if (!vs$hasShipChunkTicket(pos.toLong())) continue;

            // Vanilla already handled these chunks in the main loop.
            if (level.isNaturalSpawningAllowed(pos) &&
                ((ChunkMapAccessor) chunkMap).callAnyPlayerCloseEnoughForSpawning(pos)) continue;
            if (!level.shouldTickBlocksAt(pos.toLong())) continue;

            level.tickChunk(chunk, randomTickSpeed);
        }
    }

    private boolean vs$hasShipChunkTicket(final long chunkPos) {
        final SortedArraySet<Ticket<?>> chunkTickets =
            ((DistanceManagerAccessor) distanceManager).getTickets().get(chunkPos);
        if (chunkTickets == null) return false;

        for (final Ticket<?> ticket : chunkTickets) {
            if (ticket.getType() == VSTicketType.SHIP_CHUNK) return true;
        }

        return false;
    }
}
