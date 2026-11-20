package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.render.NativeShipPieces;

@Mixin(value = ContextShader.class, remap = false)
public class MixinFlywheelContextShader {
    @Inject(method = "onLink", at = @At("RETURN"))
    private void vs$bindPlaneSampler(GlProgram program, CallbackInfo ci) {
        program.setInt("vs_PiecePlanes", NativeShipPieces.PLANE_UNIT);
    }
}
