package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntityShipAwareLightLevel {

    @Inject(method = "getLightLevelDependentMagicValue", at = @At("HEAD"), cancellable = true)
    private void vs$shipAwareLightLevel(final CallbackInfoReturnable<Float> cir) {
        final Float result = VSGameUtilsKt.shipAwareEntityLightLevelDependentMagicValue(
            (Entity) (Object) this
        );
        if (result != null) cir.setReturnValue(result);
    }
}
