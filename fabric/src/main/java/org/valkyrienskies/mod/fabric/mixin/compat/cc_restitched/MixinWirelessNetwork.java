package org.valkyrienskies.mod.fabric.mixin.compat.cc_restitched;

import dan200.computercraft.api.network.IPacketReceiver;
import dan200.computercraft.api.network.IPacketSender;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(WirelessNetwork.class)
public class MixinWirelessNetwork {
    private static IPacketReceiver shipReceiver;
    private static IPacketSender shipSender;

    @ModifyVariable(method = "tryTransmit",
        at = @At(value = "STORE"),
        name = "distanceSq",
        remap = false
    )
    private static double ValkyrienSkies2$distanceSq(final double original) {
        final Vec3 posOnShip = shipReceiver.getPosition();
        final Vec3 posInWorld = shipSender.getPosition();

        return ValkyrienSkies.distanceSquared(shipReceiver.getLevel(),
            posOnShip.x, posOnShip.y, posOnShip.z, posInWorld.x, posInWorld.y, posInWorld.z);
    }

    @Inject(at = @At("HEAD"), method = "tryTransmit", remap = false)
    private static void ValkyrienSkies2$tryTransmit(final IPacketReceiver receiver, final Packet packet,
        final double range,
        final boolean interdimensional, final CallbackInfo ci) {
        shipReceiver = receiver;
        shipSender = packet.sender();
    }
}
