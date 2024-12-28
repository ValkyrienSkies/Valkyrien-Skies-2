package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.api.network.IPacketReceiver;
import dan200.computercraft.api.network.IPacketSender;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(WirelessNetwork.class)
public class MixinWirelessNetwork {
    private static IPacketReceiver shipReceiver;
    private static IPacketSender shipSender;

    @Redirect(
        method = "tryTransmit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private static double ValkyrienSkies$distanceToSqr(final Vec3 instance, final Vec3 d) {
        return ValkyrienSkies.distanceSquared(shipReceiver.getLevel(), instance.x, instance.y,
            instance.z, d.x, d.y, d.z);
    }

    @Inject(at = @At("HEAD"), method = "tryTransmit", remap = false)
    private static void ValkyrienSkies2$tryTransmit(final IPacketReceiver receiver, final Packet packet,
        final double range, final boolean interdimensional, final CallbackInfo ci) {
        shipReceiver = receiver;
        shipSender = packet.sender();
    }
}
