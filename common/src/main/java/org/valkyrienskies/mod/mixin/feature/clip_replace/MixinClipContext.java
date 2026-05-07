package org.valkyrienskies.mod.mixin.feature.clip_replace;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.world.RayCastExtraParameter;
import org.valkyrienskies.mod.mixinducks.feature.clip_replace.ClipContextDuck;

@Mixin(ClipContext.class)
public class MixinClipContext implements ClipContextDuck {

    @Unique
    private static RayCastExtraParameter defaultParameters = new RayCastExtraParameter();

    @Unique
    private RayCastExtraParameter parameters = new RayCastExtraParameter();

    @Override
    public void setRayCastExtraParameter(RayCastExtraParameter parameters) {
        if (parameters == null) {
            reset();
            return;
        }
        this.parameters = parameters;
    }

    @Override
    public RayCastExtraParameter getRayCastExtraParameter() {
        return parameters;
    }

    @Override
    public void reset() {
        parameters = defaultParameters;
    }

    @Inject(method = "getFrom", at = @At("HEAD"), cancellable = true)
    private void returnedShipRealStart(CallbackInfoReturnable<Vec3> cir) {
        if (parameters.get_posStart() != null) {
            cir.setReturnValue(parameters.get_posStart());
        }
    }
    @Inject(method = "getTo", at = @At("HEAD"), cancellable = true)
    private void returnedShipRealEnd(CallbackInfoReturnable<Vec3> cir) {
        if (parameters.get_posEnd() != null) {
            cir.setReturnValue(parameters.get_posEnd());
        }
    }
}
