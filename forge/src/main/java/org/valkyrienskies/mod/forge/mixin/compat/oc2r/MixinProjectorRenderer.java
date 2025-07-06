package org.valkyrienskies.mod.forge.mixin.compat.oc2r;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc2.client.renderer.blockentity.ProjectorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(ProjectorRenderer.class)
public class MixinProjectorRenderer {
    @Inject(method = "canSeeProjectedImage", at = @At("RETURN"), cancellable = true, remap = false)
    private static void valkyrienskies$alwaysRenderProjectorOnShips(PoseStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
