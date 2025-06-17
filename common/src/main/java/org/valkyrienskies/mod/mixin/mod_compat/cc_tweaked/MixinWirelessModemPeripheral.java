package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral;

import net.minecraft.world.phys.Vec3;

import org.valkyrienskies.mod.common.VSGameUtilsKt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = WirelessModemPeripheral.class, priority = 2000)
public abstract class MixinWirelessModemPeripheral {
    @WrapOperation(
        method = "getRange",
        at = @At(
            value = "INVOKE",
            target = "Ldan200/computercraft/shared/peripheral/modem/wireless/WirelessModemPeripheral;getPosition()Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 getRange$getPosition(WirelessModemPeripheral modem, Operation<Vec3> original){
        return VSGameUtilsKt.toWorldCoordinates(modem.getLevel(), original.call(modem));
    }
}
