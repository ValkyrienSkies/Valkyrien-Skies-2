package org.valkyrienskies.mod.fabric.mixin.client.render;

import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.fluid.client.ShaderNamespaceHelper;

/**
 * Vanilla {@link EffectInstance} constructs shader-program {@link ResourceLocation}s by string-concatenating
 * {@code "shaders/program/" + name + ".json"}, which corrupts any {@code "namespace:name"} input (the colon
 * ends up inside the namespace, which fails {@code assertValidNamespace}). Forge patches this; Fabric has no
 * equivalent patch, so modded post-processing shaders under a namespace throw at load time. This mixin
 * mirrors Forge's behavior by splitting the namespace off the combined string before passing it to the
 * {@code ResourceLocation} constructor.
 */
@Mixin(EffectInstance.class)
public abstract class MixinEffectInstance {

    @Redirect(
        method = {
            "<init>(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/lang/String;)V",
            "getOrCreate(Lnet/minecraft/server/packs/resources/ResourceManager;Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;)Lcom/mojang/blaze3d/shaders/EffectProgram;"
        },
        at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
    )
    private static ResourceLocation valkyrienskies$splitNamespacedShaderPath(final String combined) {
        return ShaderNamespaceHelper.splitNamespacedShaderPath(combined, "shaders/program/");
    }
}
