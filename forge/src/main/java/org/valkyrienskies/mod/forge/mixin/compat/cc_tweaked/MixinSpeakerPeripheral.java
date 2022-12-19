package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(SpeakerPeripheral.class)
public abstract class MixinSpeakerPeripheral {
    @Redirect(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Ldan200/computercraft/shared/peripheral/speaker/SpeakerPosition;position()Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 ValkyrienSkies$position(final SpeakerPosition instance) {
        Vec3 pos = instance.position();
        final Ship ship = VSGameUtilsKt.getShipObjectManagingPos(instance.level(), pos.x, pos.y, pos.z);
        if (ship != null) {
            pos = VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, pos.x, pos.y, pos.z));
        }

        return pos;
    }
}
