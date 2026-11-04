package org.valkyrienskies.mod.mixin.mod_compat.iris.complementary;

import java.util.Optional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFog;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFluidCompat;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.programs.ProgramSource", remap = false)
public abstract class MixinComplementarySource {
    @Shadow @Final private String name;

    @Inject(method = "getFragmentSource", at = @At("RETURN"), cancellable = true)
    private void vs$complementaryFog(final CallbackInfoReturnable<Optional<String>> cir) {
        cir.setReturnValue(cir.getReturnValue().map(source -> ComplementaryFluidCompat.fragment(name, org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryVolumetrics.fragment(name, ComplementaryFog.fragment(name, source)))));
    }

    @Inject(method = "getVertexSource", at = @At("RETURN"), cancellable = true)
    private void vs$complementarySurface(final CallbackInfoReturnable<Optional<String>> cir) {
        cir.setReturnValue(cir.getReturnValue().map(source -> ComplementaryFluidCompat.vertex(name, source)));
    }
}
