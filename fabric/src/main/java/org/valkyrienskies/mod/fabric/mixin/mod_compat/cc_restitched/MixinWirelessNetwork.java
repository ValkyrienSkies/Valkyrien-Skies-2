package org.valkyrienskies.mod.fabric.mixin.mod_compat.cc_restitched;

import dan200.computercraft.api.network.IPacketReceiver;
import dan200.computercraft.api.network.IPacketSender;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.fabric.common.ValkyrienSkiesModFabric;

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
    private static double ValkyrienSkies2$distanceSq(double original) {
        System.err.println("This Mixin was called!");

        Vec3 recPos = shipReceiver.getPosition();
        Vec3 senPos = shipSender.getPosition();
        double distance = VSGameUtilsKt.squaredDistanceBetweenInclShips(shipReceiver.getWorld(), recPos.x, recPos.y, recPos.z, senPos.x, senPos.y, senPos.z);

        System.err.println("Receiver: " + recPos.toString());
        System.err.println("Sneder: " + senPos.toString());
        System.err.println("Distance: " + distance);

        return distance;
    }

    @Inject(at = @At("HEAD"), method = "tryTransmit", remap = false)
    private static void ValkyrienSkies2$tryTransmit(IPacketReceiver receiver, Packet packet, double range, boolean interdimensional, CallbackInfo ci) {
        shipReceiver = receiver;
        shipSender = packet.getSender();
    }
}
