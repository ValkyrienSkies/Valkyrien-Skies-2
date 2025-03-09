package org.valkyrienskies.mod.mixin.mod_compat.immersive_portals;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader.ChunkPosConsumer;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;

/**
 * This mixin ensures that ship chunks are sent to players
 */
@Mixin(ImmPtlChunkTracking.class)
public class MixinImmPtlChunkTracking {

    @Redirect(
        method = "updateForPlayer",
        at = @At(value = "INVOKE",
            target = "Lqouteall/imm_ptl/core/chunk_loading/ChunkLoader;foreachChunkPos(Lqouteall/imm_ptl/core/chunk_loading/ChunkLoader$ChunkPosConsumer;)V")
    )
    private static void addShipChunks(final ChunkLoader instance, final ChunkPosConsumer func, @Local final ServerLevel world) {
        // region original function
        for (int dx = -instance.radius(); dx <= instance.radius(); dx++) {
            for (int dz = -instance.radius(); dz <= instance.radius(); dz++) {
                func.consume(
                    instance.getCenter().dimension,
                    instance.getCenter().x + dx,
                    instance.getCenter().z + dz,
                    Math.max(Math.abs(dx), Math.abs(dz))
                );
            }
        }
        // endregion

        // region inject ships
        final AABBd box = new AABBd(
            (instance.getCenter().x - instance.radius()) << 4,
            world.getMinBuildHeight(),
            (instance.getCenter().z - instance.radius()) << 4,
            (instance.getCenter().x + instance.radius()) << 4,
            world.getMaxBuildHeight(),
            (instance.getCenter().z + instance.radius()) << 4
        );
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(world, box)) {
            ship.getActiveChunksSet().forEach((x, z) -> {
                func.consume(
                    instance.getCenter().dimension,
                    x,
                    z,
                    1 // todo: change this?
                );
            });
        }
        // endregion
    }

}
