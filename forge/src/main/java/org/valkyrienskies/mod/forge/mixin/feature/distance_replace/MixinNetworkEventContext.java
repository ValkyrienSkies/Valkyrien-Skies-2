package org.valkyrienskies.mod.forge.mixin.feature.distance_replace;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;

// Modded server-bound packets run their logic through enqueueWork; give it the sender's level.
@Mixin(value = NetworkEvent.Context.class, remap = false)
public abstract class MixinNetworkEventContext {

    @Shadow
    public abstract ServerPlayer getSender();

    @ModifyVariable(method = "enqueueWork", at = @At("HEAD"), argsOnly = true)
    private Runnable vs$setDistanceContextLevel(final Runnable work) {
        final ServerPlayer sender = getSender();
        return sender == null ? work : ShipAwareDistance.bindLevel(sender.serverLevel(), work);
    }
}
