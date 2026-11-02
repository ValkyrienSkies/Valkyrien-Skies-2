package org.valkyrienskies.mod.mixin.mod_compat.iris;

import java.util.HashSet;
import java.util.Set;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionTextureState;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramSamplers", remap = false)
public abstract class MixinFluidProgramSamplers {
    @ModifyVariable(method = "builder", at = @At("HEAD"), argsOnly = true)
    private static Set<Integer> vs$reserveFluidSamplers(final Set<Integer> reserved, final int program,
                                                       final Set<Integer> original) {
        if (GL20.glGetUniformLocation(program, "VsFluidOverflowMasks") < 0) return reserved;
        final Set<Integer> result = new HashSet<>(reserved);
        result.add(FluidOcclusionTextureState.MASK_UNIT);
        result.add(FluidOcclusionTextureState.DESCRIPTOR_UNIT);
        return result;
    }
}
