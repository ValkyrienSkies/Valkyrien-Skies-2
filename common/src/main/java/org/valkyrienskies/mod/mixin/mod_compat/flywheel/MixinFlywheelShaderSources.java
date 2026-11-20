package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.sugar.Local;
import dev.engine_room.flywheel.backend.glsl.SourceFile;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = SourceFile.class, remap = false)
public class MixinFlywheelShaderSources {
    @ModifyVariable(method = "parse", at = @At("HEAD"), argsOnly = true)
    private static String vs$pieceShader(String source, @Local(argsOnly = true) ResourceLocation id) {
        if (!id.getNamespace().equals("flywheel")) return source;
        return switch (id.getPath()) {
            case "internal/indirect/main.vert", "internal/instancing/main.vert" ->
                source.replace("flywheel:internal/common.vert", "valkyrienskies:portal/common.vert");
            case "internal/indirect/main.frag", "internal/instancing/main.frag" ->
                source.replace("flywheel:internal/common.frag", "valkyrienskies:portal/common.frag");
            case "internal/indirect/cull.glsl" -> source.replace("_flw_isVisible(instanceIndex, modelIndex)",
                "(_flw_models[modelIndex].boundingSphere.radius < 0.0 || _flw_isVisible(instanceIndex, modelIndex))");
            default -> source;
        };
    }
}
