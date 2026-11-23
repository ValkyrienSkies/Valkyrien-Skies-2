package org.valkyrienskies.mod.fabric.mixin.client.render;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.fluid.client.ShaderNamespaceHelper;

/**
 * Same {@code "namespace:name"}-gets-mangled bug as {@code EffectInstance}: vanilla
 * {@link ShaderInstance} builds {@code new ResourceLocation("shaders/program/" + name + ".json")} which
 * fails validation when {@code name} contains a namespace prefix. Forge patches this; Fabric needs the
 * same split-on-colon fix for modded program shaders to load.
 */
@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstanceLoader {

    @Redirect(
        method = {
            "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            "getOrCreate(Lnet/minecraft/server/packs/resources/ResourceProvider;Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;)Lcom/mojang/blaze3d/shaders/Program;"
        },
        at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
    )
    private static ResourceLocation valkyrienskies$splitNamespacedShaderPath(final String combined) {
        return ShaderNamespaceHelper.splitNamespacedShaderPath(combined, "shaders/program/");
    }
}
