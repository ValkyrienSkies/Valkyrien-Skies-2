package org.valkyrienskies.mod.mixin.mod_compat.computercraft;

import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(SpeakerPeripheral.class)
public abstract class MixinSpeakerPeripheral {
    @Shadow
    public abstract Level getWorld();

    @Redirect(
        method = "playSound(Ldan200/computercraft/api/lua/ILuaContext;Lnet/minecraft/resources/ResourceLocation;FFZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Ldan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral;getPosition()Lnet/minecraft/world/phys/Vec3;"
        ),
        remap = false
    )
    public Vec3 getPosition(final SpeakerPeripheral instance) {
        Vec3 pos = instance.getPosition();
        final Ship ship = VSGameUtilsKt.getShipObjectManagingPos(this.getWorld(), pos.x, pos.y, pos.z);
        if (ship != null) {
            pos = VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, pos.x, pos.y, pos.z));
        }
        return pos;
    }

}
