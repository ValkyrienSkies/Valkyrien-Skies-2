package org.valkyrienskies.mod.mixin.mod_compat.iris;

import java.util.Optional;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.compat.flooding.shaders.bliss.BlissFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.bsl.BslFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.derivative.DerivativeFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFog;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryVolumetrics;
import org.valkyrienskies.mod.compat.flooding.shaders.iterationrp.IterationRpFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.photon.PhotonFluidCompat;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.programs.ProgramSource", remap = false)
public abstract class MixinShaderFluidSource {
    @Shadow @Final private String name;
    @Shadow public abstract ProgramSet getParent();

    @Inject(method = "getFragmentSource", at = @At("RETURN"), cancellable = true)
    private void vs$patchFluidFragment(final CallbackInfoReturnable<Optional<String>> cir) {
        cir.setReturnValue(cir.getReturnValue().map(source -> {
            final String complementary = ComplementaryFluidCompat.fragment(name,
                ComplementaryVolumetrics.fragment(name, ComplementaryFog.fragment(name, source)));
            final String patched = IterationRpFluidCompat.fragment(name, DerivativeFluidCompat.fragment(name,
                BlissFluidCompat.fragment(name, PhotonFluidCompat.fragment(name, complementary))));
            return BslFluidCompat.fragment(name, patched,
                () -> Optional.ofNullable(getParent().getComposite(ProgramArrayId.Composite)[0])
                    .flatMap(ProgramSource::getFragmentSource).orElse(""));
        }));
    }

    @Inject(method = "getVertexSource", at = @At("RETURN"), cancellable = true)
    private void vs$patchFluidVertex(final CallbackInfoReturnable<Optional<String>> cir) {
        cir.setReturnValue(cir.getReturnValue().map(source -> BslFluidCompat.vertex(name, IterationRpFluidCompat.vertex(name,
            DerivativeFluidCompat.vertex(name, BlissFluidCompat.vertex(name,
                PhotonFluidCompat.vertex(name, ComplementaryFluidCompat.vertex(name, source))))))));
    }

    @Inject(method = "getGeometrySource", at = @At("RETURN"), cancellable = true)
    private void vs$patchFluidGeometry(final CallbackInfoReturnable<Optional<String>> cir) {
        cir.setReturnValue(cir.getReturnValue().map(source -> IterationRpFluidCompat.geometry(name, source)));
    }
}
