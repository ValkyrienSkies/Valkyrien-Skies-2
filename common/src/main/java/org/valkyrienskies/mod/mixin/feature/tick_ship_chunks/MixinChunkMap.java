package org.valkyrienskies.mod.mixin.feature.tick_ship_chunks;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ServerShip;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * These methods fix random ticking on ship chunks
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "euclideanDistanceSquared", at = @At("HEAD"), cancellable = true)
    private static void preDistanceToSqr(final ChunkPos chunkPos, final Entity entity,
        final CallbackInfoReturnable<Double> cir) {
        final double d = chunkPos.x * 16 + 8;
        final double e = chunkPos.z * 16 + 8;
        final double retValue =
            VSGameUtilsKt.squaredDistanceBetweenInclShips(entity.level, entity.getX(), 0, entity.getZ(), d,
                0,
                e);

        cir.setReturnValue(retValue);
    }

    @Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("RETURN"), cancellable = true)
    void noPlayersCloseForSpawning(final ChunkPos chunkPos, final CallbackInfoReturnable<Boolean> cir) {
        if (ChunkAllocator.isChunkInShipyard(chunkPos.x, chunkPos.z)) {
            if (cir.getReturnValue()) {
                final ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()
                    .getShipDataFromChunkPos(chunkPos.x, chunkPos.z, VSGameUtilsKt.getDimensionId(level));
                if (ship != null) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

}
