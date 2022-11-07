package org.valkyrienskies.mod.fabric.mixin.mod_compat.cc_restitched;

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
import org.valkyrienskies.mod.common.VSGameUtilsKt;

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
    private static double ValkyrienIsles2$distanceSq(double original) {
        Vec3 recPos = shipReceiver.getPosition();
        Vec3 senPos = shipSender.getPosition();
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(shipReceiver.getWorld(), recPos.x, recPos.y, recPos.z, senPos.x, senPos.y, senPos.z);
    }

    @Inject(at = @At("HEAD"), method = "tryTransmit", remap = false)
    private static void ValkyrienIsles2$tryTransmit(IPacketReceiver receiver, Packet packet, double range, boolean interdimensional, CallbackInfo ci) {
        shipReceiver = receiver;
        shipSender = packet.getSender();
    }
}
