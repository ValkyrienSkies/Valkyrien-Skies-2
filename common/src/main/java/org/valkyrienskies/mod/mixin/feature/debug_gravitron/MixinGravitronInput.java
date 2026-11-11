package org.valkyrienskies.mod.mixin.feature.debug_gravitron;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.item.DebugGravitronItem;
import org.valkyrienskies.mod.common.networking.PacketGravitronFreeze;

@Mixin(Minecraft.class)
public abstract class MixinGravitronInput {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void vs$freezeShip(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player != null && minecraft.player.getMainHandItem().getItem() instanceof DebugGravitronItem) {
            ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(new PacketGravitronFreeze());
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void vs$stopBlockDamage(boolean held, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player != null && minecraft.player.getMainHandItem().getItem() instanceof DebugGravitronItem) {
            ci.cancel();
        }
    }
}
