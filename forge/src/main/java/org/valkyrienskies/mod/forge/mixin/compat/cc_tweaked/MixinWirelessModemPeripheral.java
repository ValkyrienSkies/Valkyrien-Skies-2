package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(WirelessModemPeripheral.class)
public abstract class MixinWirelessModemPeripheral {
    @WrapOperation(
            method = "getRange",
            at = @At(
                    value = "INVOKE",
                    target = "Ldan200/computercraft/shared/peripheral/modem/wireless/WirelessModemPeripheral;getPosition()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    public Vec3 ValkyrienSkies$getPosition(WirelessModemPeripheral instance, Operation<Vec3> original){
        return VSGameUtilsKt.toWorldCoordinates(instance.getLevel(), original.call(instance));
    }
}
