package org.valkyrienskies.mod.mixin.feature.fluid_render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.client.ShipExteriorFluidSampler;
import org.valkyrienskies.mod.mixin.accessors.network.protocol.game.ClientboundSectionBlocksUpdatePacketAccessor;

/**
 * Retires cached world fluid surfaces when the client learns a chunk changed.
 *
 * <p>{@link ShipExteriorFluidSampler} caches per-block surface heights keyed on a chunk revision, so
 * without these bumps a shoreline or a drained pool would keep reporting its old waterline and ship
 * fluid geometry would be built against a surface that is no longer there.</p>
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void vs$invalidateFluidSurfacesOnChunkLoad(final ClientboundLevelChunkWithLightPacket packet,
        final CallbackInfo ci) {
        ShipExteriorFluidSampler.invalidateExteriorFluidChunk(level, packet.getX(), packet.getZ());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void vs$invalidateFluidSurfacesOnSectionUpdate(final ClientboundSectionBlocksUpdatePacket packet,
        final CallbackInfo ci) {
        final SectionPos pos = ((ClientboundSectionBlocksUpdatePacketAccessor) packet).getSectionPos();
        ShipExteriorFluidSampler.invalidateExteriorFluidChunk(level, pos.x(), pos.z());
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void vs$invalidateFluidSurfacesOnBlockUpdate(final ClientboundBlockUpdatePacket packet,
        final CallbackInfo ci) {
        ShipExteriorFluidSampler.invalidateExteriorFluidChunk(
            level, packet.getPos().getX() >> 4, packet.getPos().getZ() >> 4);
    }
}
