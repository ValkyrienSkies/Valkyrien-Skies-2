package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Mob.class)
public abstract class MixinMobConvertTo {

    @Inject(method = "convertTo", at = @At("RETURN"))
    private void vs$inheritShipAttachment(final CallbackInfoReturnable<Mob> cir) {
        final Mob converted = cir.getReturnValue();
        if (converted == null) return;
        final Long parentShipId = ((IEntityDraggingInformationProvider) this)
            .getDraggingInformation().getLastShipStoodOn();
        if (parentShipId == null) return;
        final Ship ship = VSGameUtilsKt.getAllShips(((Mob) (Object) this).level()).getById(parentShipId);
        if (ship == null) return;
        ((IEntityDraggingInformationProvider) converted).vs$dragImmediately(ship);
    }
}
