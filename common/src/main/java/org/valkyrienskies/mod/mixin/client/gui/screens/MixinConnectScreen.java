package org.valkyrienskies.mod.mixin.client.gui.screens;

import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mixin(ConnectScreen.class)
public class MixinConnectScreen {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void preStartConnecting(final CallbackInfo ci) {
        ValkyrienSkiesMod.getVsCore().setClientUsesUDP(false);
    }
}
