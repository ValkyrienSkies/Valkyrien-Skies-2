package org.valkyrienskies.mod.mixin.feature.fix_frustum_dead_loop;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public class MixinFrustum {
    @Unique
    private int vs$loopCount = 0; // Frustum objects are newly created each frame so no need to reset this to zero every tick.

    // this is called after each iteration of the for loop in this method
    // a check to make sure it doesn't get stuck in a dead loop, max of 10 is arbitrary except it doesn't seem to get called in
    // normal gameplay
    @Inject(method = "offsetToFullyIncludeCameraCube", at = @At(value = "INVOKE",
        target = "Lorg/joml/FrustumIntersection;intersectAab(FFFFFF)I"), cancellable = true)
    private void checkIfItsExceeded(int i, CallbackInfoReturnable<Frustum> cir) {
        if (vs$loopCount > 10) {
            cir.setReturnValue((Frustum) (Object) this); // put a breakpoint here if you want to see when this happens
            vs$loopCount = 0;
        } else {
            vs$loopCount++;
        }
    }
}
