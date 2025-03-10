package org.valkyrienskies.mod.mixin.mod_compat.optifine_vanilla;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

/**
 * This mixin allows {@link LevelRenderer} to render ship chunks.
 */

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Shadow
    private ClientLevel level;
    @Shadow
    private ViewArea viewArea;

    /**
     * Prevents ships from disappearing on f3+a
     */
    @Inject(
        method = "allChanged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(DD)V"
        )
    )
    private void afterRefresh(final CallbackInfo ci) {
        // This can happen when immersive portals is installed
        if (!(this.level.getChunkSource() instanceof final ClientChunkCacheDuck chunks)) return;

        chunks.vs$getShipChunks().forEach((pos, chunk) -> {
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                viewArea.setDirty(ChunkPos.getX(pos), y, ChunkPos.getZ(pos), false);
            }
        });
    }
}

