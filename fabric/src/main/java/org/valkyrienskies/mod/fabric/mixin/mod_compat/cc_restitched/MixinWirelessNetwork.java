package org.valkyrienskies.mod.fabric.mixin.mod_compat.cc_restitched;

import dan200.computercraft.api.network.IPacketReceiver;
import dan200.computercraft.api.network.IPacketSender;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(WirelessNetwork.class)
public class MixinWirelessNetwork {
    private static IPacketReceiver shipReceiver;
    private static IPacketSender shipSender;

    /*@Redirect(method = "tryTransmit",
        at = @At(value = "FIELD",
            target = "Ldan200/computercraft/shared/peripheral/modem/wireless/WirelessNetwork.tryTransmit();sqDistance",
            opcode = Opcodes.PUTFIELD)
    )
    private void ValkyrienIsles2$sqDistance(double owner, double sqDistance) {
        Vec3 recPos = shipReceiver.getPosition();
        Vec3 senPos = shipSender.getPosition();
        owner = VSGameUtilsKt.squaredDistanceBetweenInclShips(shipReceiver.getWorld(), recPos.x, recPos.y, recPos.z, senPos.x, senPos.y, senPos.z);
    }*/

    @ModifyVariable(method = "tryTransmit",
        at = @At(value = "INVOKE_ASSIGN", target = "Ldan200/computercraft/shared/peripheral/modem/wireless/WirelessNetwork;tryTransmit/distanceToSqr()D"),
        index = 1
    )
    public double ValkyrienIsles2$sqDistance(double original) {
        Vec3 recPos = shipReceiver.getPosition();
        Vec3 senPos = shipSender.getPosition();
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(shipReceiver.getWorld(), recPos.x, recPos.y, recPos.z, senPos.x, senPos.y, senPos.z);
    }

    @Inject(at = @At("HEAD"), method = "tryTransmit")
    public static void ValkyrienIsles2$tryTransmit(IPacketReceiver receiver, Packet packet, double range, boolean interdimensional, CallbackInfo ci) {
        shipReceiver = receiver;
        shipSender = packet.getSender();
    }
}
