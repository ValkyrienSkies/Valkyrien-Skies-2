package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(SpeakerPosition.class)
public abstract class MixinSpeakerPosition {
    @Shadow
    public abstract Level level();

    @Inject(method = "position", at = @At("RETURN"), remap = false, cancellable = false)
    public void ValkyrienSkies2$getPosition(CallbackInfoReturnable cir) {
        Vec3 pos = (Vec3) cir.getReturnValue();
        final Ship ship = VSGameUtilsKt.getShipObjectManagingPos(this.level(), pos.x, pos.y, pos.z);
        if (ship != null) {
            cir.setReturnValue(VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, pos.x, pos.y, pos.z)));
        }
    }
}
