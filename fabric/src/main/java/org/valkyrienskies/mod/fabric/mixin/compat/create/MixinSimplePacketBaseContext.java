package org.valkyrienskies.mod.fabric.mixin.compat.create;

import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;

// Create server-bound packets run their logic through enqueueWork; give it the sender's level.
@Mixin(value = SimplePacketBase.Context.class, remap = false)
public abstract class MixinSimplePacketBaseContext {

    @Shadow
    public abstract ServerPlayer sender();

    @ModifyVariable(method = "enqueueWork", at = @At("HEAD"), argsOnly = true)
    private Runnable vs$setDistanceContextLevel(final Runnable work) {
        final ServerPlayer sender = sender();
        return sender == null ? work : ShipAwareDistance.bindLevel(sender.serverLevel(), work);
    }
}
