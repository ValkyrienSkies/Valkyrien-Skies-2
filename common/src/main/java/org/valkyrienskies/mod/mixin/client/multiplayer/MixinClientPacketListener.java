package org.valkyrienskies.mod.mixin.client.multiplayer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    public void afterLogin(final ClientboundLoginPacket packet, final CallbackInfo ci) {
        Minecraft.getInstance().player.sendMessage(
            new TextComponent("You are using an ALPHA version of Valkyrien Skies 2, use at your own risk!").withStyle(
                ChatFormatting.RED, ChatFormatting.BOLD),
            null);
    }
}
