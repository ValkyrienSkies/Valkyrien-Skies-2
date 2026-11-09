package org.valkyrienskies.mod.mixin.mod_compat.iris;

import java.util.HashSet;
import java.util.Set;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionTextureState;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderFluidFog;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramSamplers", remap = false)
public abstract class MixinFluidProgramSamplers {
    @ModifyVariable(method = "builder", at = @At("HEAD"), argsOnly = true)
    private static Set<Integer> vs$reserveFluidSamplers(final Set<Integer> reserved, final int program,
                                                       final Set<Integer> original) {
        final boolean masks = GL20.glGetUniformLocation(program, "VsFluidOverflowMasks") >= 0;
        final boolean fog = GL20.glGetUniformLocation(program, "vs_ExteriorWaterPath") >= 0;
        if (!masks && !fog) return reserved;
        final Set<Integer> result = new HashSet<>(reserved);
        if (masks) {
            result.add(FluidOcclusionTextureState.MASK_UNIT);
            result.add(FluidOcclusionTextureState.DESCRIPTOR_UNIT);
        }
        if (fog) result.add(ShaderFluidFog.TEXTURE_UNIT);
        if (GL20.glGetUniformLocation(program, "vs_ExteriorWorldWater") >= 0)
            result.add(ShaderFluidFog.WATER_FIELD_UNIT);
        return result;
    }
}
