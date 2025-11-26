package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;

import net.minecraft.world.phys.Vec3;

import org.valkyrienskies.mod.common.VSGameUtilsKt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = WirelessNetwork.class, priority = 2000)
public class MixinWirelessNetwork {
    @Redirect(
        method = "tryTransmit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private static double tryTransmit$distanceToSqr(final Vec3 origin, final Vec3 pos, final PacketReceiver receiver) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            receiver.getLevel(),
            origin.x, origin.y, origin.z,
            pos.x, pos.y, pos.z
        );
    }
}
