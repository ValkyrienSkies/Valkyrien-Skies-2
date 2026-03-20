package org.valkyrienskies.mod.mixin.feature.air_pockets.compat.embeddium;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketShaderInjector;

@Pseudo
@Mixin(
    targets = {
        "me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader"
    },
    remap = false
)
public abstract class MixinSodiumShaderLoader {

    @Inject(
        method = "getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private static void valkyrienair$patchSodiumShaderSource(final ResourceLocation identifier,
        final CallbackInfoReturnable<String> cir) {
        final String original = cir.getReturnValue();
        final String patched = ShipWaterPocketShaderInjector.injectSodiumShader(identifier, original);
        if (!Objects.equals(original, patched)) {
            cir.setReturnValue(patched);
        }
    }
}
