package org.valkyrienskies.mod.mixin.client.multiplayer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLogin", at = @At("TAIL"))
    public void afterLogin(final ClientboundLoginPacket packet, final CallbackInfo ci) {
        Minecraft.getInstance().player.sendMessage(
            new TextComponent("You are using an ALPHA version of Valkyrien Skies 2, use at your own risk!").withStyle(
                ChatFormatting.RED, ChatFormatting.BOLD),
            null);
    }

    /**
     * Spawn [ShipMountingEntity] on client side
     */
    @Inject(method = "handleAddEntity",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"), cancellable = true)
    private void handleShipMountingEntity(final ClientboundAddEntityPacket packet, final CallbackInfo ci) {
        if (packet.getType().equals(ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE)) {
            ci.cancel();
            final double d = packet.getX();
            final double e = packet.getY();
            final double f = packet.getZ();
            final Entity entity = ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE.create(level);
            final int i = packet.getId();
            entity.setPacketCoordinates(d, e, f);
            entity.moveTo(d, e, f);
            entity.xRot = (float) (packet.getxRot() * 360) / 256.0f;
            entity.yRot = (float) (packet.getyRot() * 360) / 256.0f;
            entity.setId(i);
            entity.setUUID(packet.getUUID());
            this.level.putNonPlayerEntity(i, entity);
        }
    }
}
