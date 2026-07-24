package org.valkyrienskies.mod.mixin.feature.air_pockets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketCurrentShipRenderContext;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCull;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCullRenderContext;
import org.valkyrienskies.mod.air_pockets.client.ShipInteriorFogRenderer;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketLiquidOverlay;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;
import org.valkyrienskies.mod.util.ClientConnectivityUpdateQueue;

@Mixin(value = Minecraft.class, priority = 900)
public abstract class MixinMinecraft {

    @Inject(method = "tick", at = @At("TAIL"))
    private void valkyrienair$tickShipWaterPockets(final CallbackInfo ci) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;

        final ClientLevel level = mc.level;
        if (level == null) return;

        ShipWaterPocketManager.tickClientLevel(level);
        ClientConnectivityUpdateQueue.drainQueuedChunks(8);
        if (level.getChunkSource() instanceof final ClientChunkCacheDuck chunkCache) {
            chunkCache.vs$drainShipChunkUnloadQueue();
        }
    }

    @Inject(method = "clearLevel", at = @At("TAIL"))
    private void valkyrienair$clearShipWaterPocketRenderer(final CallbackInfo ci) {
        ShipWaterPocketExternalWaterCull.clear();
        ShipWaterPocketExternalWaterCullRenderContext.clear();
        ShipWaterPocketCurrentShipRenderContext.clear();
        ShipInteriorFogRenderer.clear();
        ShipWaterPocketLiquidOverlay.clear();
    }
}
