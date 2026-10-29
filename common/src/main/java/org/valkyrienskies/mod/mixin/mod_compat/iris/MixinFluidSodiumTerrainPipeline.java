package org.valkyrienskies.mod.mixin.mod_compat.iris;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionShader;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.SodiumTerrainPipeline", remap = false)
public abstract class MixinFluidSodiumTerrainPipeline {
    @Shadow Optional<String> terrainSolidVertex;
    @Shadow Optional<String> terrainSolidGeometry;
    @Shadow Optional<String> terrainSolidTessControl;
    @Shadow Optional<String> terrainSolidTessEval;
    @Shadow Optional<String> terrainSolidFragment;
    @Shadow Optional<String> terrainCutoutVertex;
    @Shadow Optional<String> terrainCutoutGeometry;
    @Shadow Optional<String> terrainCutoutTessControl;
    @Shadow Optional<String> terrainCutoutTessEval;
    @Shadow Optional<String> terrainCutoutFragment;
    @Shadow Optional<String> translucentVertex;
    @Shadow Optional<String> translucentGeometry;
    @Shadow Optional<String> translucentTessControl;
    @Shadow Optional<String> translucentTessEval;
    @Shadow Optional<String> translucentFragment;

    @Inject(method = "patchShaders", at = @At("RETURN"))
    private void vs$patchFluidTerrain(final CallbackInfo ci) {
        if (terrainSolidVertex.isPresent() && terrainSolidFragment.isPresent()) {
            if (terrainSolidGeometry.isEmpty() && terrainSolidTessControl.isEmpty() && terrainSolidTessEval.isEmpty()
                && FluidOcclusionShader.canPatch(terrainSolidVertex.get())
                && FluidOcclusionShader.canPatch(terrainSolidFragment.get())) {
                terrainSolidVertex = terrainSolidVertex.map(source -> FluidOcclusionShader.vertex(source, true));
                terrainSolidFragment = terrainSolidFragment.map(FluidOcclusionShader::fragment);
            } else {
                LogManager.getLogger("Valkyrien Skies Fluid Occlusion").warn(
                    "Fluid masking unavailable for Iris terrainSolid: unsupported shader stages or entry point");
            }
        }
        if (terrainCutoutVertex.isPresent() && terrainCutoutFragment.isPresent()) {
            if (terrainCutoutGeometry.isEmpty() && terrainCutoutTessControl.isEmpty() && terrainCutoutTessEval.isEmpty()
                && FluidOcclusionShader.canPatch(terrainCutoutVertex.get())
                && FluidOcclusionShader.canPatch(terrainCutoutFragment.get())) {
                terrainCutoutVertex = terrainCutoutVertex.map(source -> FluidOcclusionShader.vertex(source, true));
                terrainCutoutFragment = terrainCutoutFragment.map(FluidOcclusionShader::fragment);
            } else {
                LogManager.getLogger("Valkyrien Skies Fluid Occlusion").warn(
                    "Fluid masking unavailable for Iris terrainCutout: unsupported shader stages or entry point");
            }
        }
        if (translucentVertex.isPresent() && translucentFragment.isPresent()) {
            if (translucentGeometry.isEmpty() && translucentTessControl.isEmpty() && translucentTessEval.isEmpty()
                && FluidOcclusionShader.canPatch(translucentVertex.get())
                && FluidOcclusionShader.canPatch(translucentFragment.get())) {
                translucentVertex = translucentVertex.map(source -> FluidOcclusionShader.vertex(source, true));
                translucentFragment = translucentFragment.map(FluidOcclusionShader::fragment);
            } else {
                LogManager.getLogger("Valkyrien Skies Fluid Occlusion").warn(
                    "Fluid masking unavailable for Iris translucent: unsupported shader stages or entry point");
            }
        }
    }
}
