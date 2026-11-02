package org.valkyrienskies.mod.mixin.feature.ai.sleep;

import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// On stopSleeping from a ship-mounted bed, broadcast a teleport packet immediately so the client gets the new world pose in the same broadcast pass — without it the data-tracker pose-update can race ahead of the position packet, dropping ShipMountedToData and disappearing the entity for one frame.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityWakeHandoff {

    @Unique
    private boolean vs$wokeFromShipBed;

    @Inject(method = "stopSleeping", at = @At("HEAD"))
    private void vs$captureShipBedWake(final CallbackInfo ci) {
        final LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isSleeping()) {
            this.vs$wokeFromShipBed = false;
            return;
        }
        this.vs$wokeFromShipBed = self.getSleepingPos()
            .map(pos -> VSGameUtilsKt.getShipManagingPos(self.level(), pos) != null)
            .orElse(false);
    }

    @Inject(method = "stopSleeping", at = @At("TAIL"))
    private void vs$broadcastWakeTeleport(final CallbackInfo ci) {
        if (!this.vs$wokeFromShipBed) return;
        this.vs$wokeFromShipBed = false;
        final LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.getChunkSource().broadcastAndSend(self, new ClientboundTeleportEntityPacket(self));
    }
}
