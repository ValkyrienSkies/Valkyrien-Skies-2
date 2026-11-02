package org.valkyrienskies.mod.mixin.server.world;

import java.util.Optional;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkHolderAccessor;

/**
 * Compatibility shims for active ship chunks loaded with radius-zero SHIP_CHUNK tickets.
 *
 * Vanilla only exposes getTickingChunk()/BLOCK_TICKING holder status when ticket levels reach
 * the normal ticking thresholds. Radius-zero ship chunks intentionally stop at FULL to avoid
 * the forced-ticket chunk ring, so selected holder queries need to treat the loaded full chunk
 * as ticking while the experimental server config is enabled.
 */
@Mixin(ChunkHolder.class)
public abstract class MixinChunkHolder {

    @Shadow
    @Final
    ChunkPos pos;

    @Shadow
    @Final
    private LevelHeightAccessor levelHeightAccessor;

    @Inject(method = "getTickingChunk", at = @At("HEAD"), cancellable = true)
    private void vs$getTickingChunkForRadiusZeroShipyard(CallbackInfoReturnable<LevelChunk> cir) {
        if (!vs$useRadiusZeroShipyardTicking()) return;

        final Optional<LevelChunk> fullChunk =
            ((ChunkHolderAccessor) this).getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left();
        fullChunk.ifPresent(cir::setReturnValue);
    }

    /**
     * Report radius-zero shipyard holders as BLOCK_TICKING so ChunkMap paths that rely on
     * holder status can register tick containers and process saved scheduled ticks.
     */
    @Inject(method = "getFullStatus", at = @At("HEAD"), cancellable = true)
    private void vs$upgradeRadiusZeroShipyardHolderStatus(CallbackInfoReturnable<FullChunkStatus> cir) {
        if (vs$useRadiusZeroShipyardTicking()) {
            cir.setReturnValue(FullChunkStatus.BLOCK_TICKING);
        }
    }

    private boolean vs$useRadiusZeroShipyardTicking() {
        return VSGameConfig.SERVER.getPerformance().getUseRadiusZeroShipChunkTickets()
            && VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z);
    }
}
