package org.valkyrienskies.mod.mixin.client.multiplayer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.IShipObjectWorldClientCreator;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLogin", at = @At("TAIL"))
    public void afterLogin(final ClientboundLoginPacket packet, final CallbackInfo ci) {
        Minecraft.getInstance().player.sendSystemMessage(
            Component.literal("You are using an ALPHA version of Valkyrien Skies 2, use at your own risk!").withStyle(
                ChatFormatting.RED, ChatFormatting.BOLD));
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = Shift.AFTER
        ),
        method = "handleLogin"
    )
    private void beforeHandleLogin(final ClientboundLoginPacket packet, final CallbackInfo ci) {
        ((IShipObjectWorldClientCreator) Minecraft.getInstance()).createShipObjectWorldClient();
    }

    /**
     * Spawn [ShipMountingEntity] on client side
     */
    @Inject(method = "handleAddEntity",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"),
        cancellable = true)
    private void handleShipMountingEntity(final ClientboundAddEntityPacket packet, final CallbackInfo ci) {
        if (packet.getType().equals(ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE)) {
            ci.cancel();
            final double d = packet.getX();
            final double e = packet.getY();
            final double f = packet.getZ();
            final Entity entity = ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE.create(level);
            final int i = packet.getId();
            entity.syncPacketPositionCodec(d, e, f);
            entity.moveTo(d, e, f);
            entity.setXRot((packet.getXRot() * 360) / 256.0f);
            entity.setYRot((packet.getYRot() * 360) / 256.0f);
            entity.setId(i);
            entity.setUUID(packet.getUUID());
            this.level.putNonPlayerEntity(i, entity);
        }
    }

    /**
     * When mc receives a tp packet it lerps it between 2 positions in 3 steps, this is bad for ships it gets stuck in a
     * unloaded chunk clientside and stays there until rejoining the server.
     */
    @Redirect(method = "handleTeleportEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFIZ)V"))
    private void teleportingWithNoStep(final Entity instance,
        final double x, final double y, final double z,
        final float yRot, final float xRot,
        final int lerpSteps, final boolean teleport) {
        if (VSGameUtilsKt.getShipObjectManagingPos(instance.level, instance.getX(), instance.getY(), instance.getZ()) !=
            null) {
            instance.setPos(x, y, z);
            instance.lerpTo(x, y, z, yRot, xRot, 1, teleport);
        } else {
            instance.lerpTo(x, y, z, yRot, xRot, lerpSteps, teleport);
        }
    }
}
