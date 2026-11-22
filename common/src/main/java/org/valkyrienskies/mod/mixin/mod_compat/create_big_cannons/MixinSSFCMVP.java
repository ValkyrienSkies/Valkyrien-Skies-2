package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.Executor;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;
import rbasamoyai.createbigcannons.network.ServerboundSetFixedCannonMountValuePacket;

// CBC defers the handler through the passed executor (a separate server task), outside enqueueWork's context.
@Mixin(ServerboundSetFixedCannonMountValuePacket.class)
public abstract class MixinSSFCMVP {

    @ModifyVariable(method = "handle", at = @At("HEAD"), argsOnly = true, remap = false)
    private Executor vs$setDistanceContextLevel(final Executor exec, @Local(argsOnly = true) final ServerPlayer sender) {
        return sender == null ? exec : ShipAwareDistance.bindLevel(sender.serverLevel(), exec);
    }
}
