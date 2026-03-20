package org.valkyrienskies.mod.mixin.feature.air_pockets;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.mixin.accessors.network.protocol.game.ClientboundSectionBlocksUpdatePacketAccessor;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void vs$markShipWaterPocketsDirtyOnShipyardChunkLoad(final ClientboundLevelChunkWithLightPacket packet,
        final CallbackInfo ci) {
        if (level == null) return;
        vs$markShipDirtyIfShipyardChunk(packet.getX(), packet.getZ());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void vs$markShipWaterPocketsDirtyOnShipyardSectionUpdate(final ClientboundSectionBlocksUpdatePacket packet,
        final CallbackInfo ci) {
        if (level == null) return;
        final SectionPos pos = ((ClientboundSectionBlocksUpdatePacketAccessor) packet).getSectionPos();
        vs$markShipDirtyIfShipyardChunk(pos.x(), pos.z());
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void vs$markShipWaterPocketsDirtyOnShipyardBlockUpdate(final ClientboundBlockUpdatePacket packet,
        final CallbackInfo ci) {
        if (level == null) return;
        vs$markShipDirtyIfShipyardChunk(packet.getPos().getX() >> 4, packet.getPos().getZ() >> 4);
    }

    @Unique
    private void vs$markShipDirtyIfShipyardChunk(final int chunkX, final int chunkZ) {
        if (!VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) return;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, chunkX, chunkZ);
        if (ship == null) return;
        ShipWaterPocketManager.markShipDirty(level, ship.getId());
    }
}
