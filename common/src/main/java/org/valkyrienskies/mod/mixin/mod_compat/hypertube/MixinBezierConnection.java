package org.valkyrienskies.mod.mixin.mod_compat.hypertube;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

// This is no longer necessary, but I’ll leave it here!
@Pseudo
@Mixin(targets = "com/pedrorok/hypertube/core/connection/BezierConnection", remap = false)
public abstract class MixinBezierConnection {
    @Unique
    private final static float MAX_REASONABLE_DISTANCE = 1000F;

    @Shadow
    public float distance() { return 0; }

    // The mod always tries to calculate and render the curve, even if it is ridiculously long. That results in OOM.
    @WrapOperation(method = "getValidation", at = @At(value = "INVOKE", target = "Lcom/pedrorok/hypertube/core/connection/BezierConnection;getMaxAngleBezierAngle()F"))
    private float cancelLongBezierCalculation(@Coerce Object instance, Operation<Float> original) {
        if (distance() > MAX_REASONABLE_DISTANCE) return 0;
        return original.call(instance);
    }

    @WrapMethod(method = "drawPath")
    private void cancelLongDrawPath(LerpedFloat animation, boolean isValid, Operation<Void> original) {
        if (distance() > MAX_REASONABLE_DISTANCE) return;
        original.call(animation, isValid);
    }
}
