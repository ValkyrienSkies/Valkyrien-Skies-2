package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;
import org.valkyrienskies.mod.mixin.accessors.network.protocol.game.ClientboundSectionBlocksUpdatePacketAccessor;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Shadow
    private ClientLevel level;

    @Inject(at = @At("TAIL"), method = "handleBlockUpdate")
    private void vs$sodium$markShipRenderListsDirtyAfterBlockUpdate(final ClientboundBlockUpdatePacket packet,
        final CallbackInfo ci) {
        if (level == null) return;
        final int cx = packet.getPos().getX() >> 4;
        final int cz = packet.getPos().getZ() >> 4;
        if (VSGameUtilsKt.getShipManagingPos(level, cx, cz) instanceof ClientShip ship) {
            SodiumCompat.markShipSectionCacheDirty(ship);
        }
    }

    @Inject(at = @At("TAIL"), method = "handleChunkBlocksUpdate")
    private void vs$sodium$markShipRenderListsDirtyAfterSectionBlocksUpdate(
        final ClientboundSectionBlocksUpdatePacket packet, final CallbackInfo ci) {
        if (level == null) return;
        final SectionPos pos = ((ClientboundSectionBlocksUpdatePacketAccessor) packet).getSectionPos();
        if (VSGameUtilsKt.getShipManagingPos(level, pos.x(), pos.z()) instanceof ClientShip ship) {
            SodiumCompat.markShipSectionCacheDirty(ship);
        }
    }
}
