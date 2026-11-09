package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionShader;

@Mixin(value = GlShader.class, remap = false)
public abstract class MixinFluidGlShader {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static String vs$fluidShader(final String source, final ShaderType type,
                                        final ResourceLocation name, final String original) {
        final boolean stock = name.getNamespace().equals("sodium")
            && name.getPath().startsWith("blocks/block_layer_opaque.");
        final boolean world = name.getNamespace().equals("valkyrienskies")
            && name.getPath().startsWith("blocks/world_layer_opaque.");
        if (!stock && !world) return source;
        return type == ShaderType.VERTEX ? FluidOcclusionShader.vertex(source, false)
            : type == ShaderType.FRAGMENT ? FluidOcclusionShader.fragment(source) : source;
    }
}
