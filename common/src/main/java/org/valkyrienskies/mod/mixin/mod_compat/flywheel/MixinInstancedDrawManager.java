package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDraw;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDrawManager;
import dev.engine_room.flywheel.backend.gl.TextureBuffer;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.flywheel.FlywheelShipPieces;

@Mixin(value = InstancedDrawManager.class, remap = false)
public class MixinInstancedDrawManager {
    @WrapOperation(method = {"submitDraws", "submitOitDraws"}, at = @At(value = "INVOKE", target =
        "Ldev/engine_room/flywheel/backend/engine/instancing/InstancedDraw;render(Ldev/engine_room/flywheel/backend/gl/TextureBuffer;)V"))
    private void vs$drawPieces(InstancedDraw draw, TextureBuffer buffer, Operation<Void> original, @Local GlProgram program) {
        var split = FlywheelShipPieces.split(draw.groupKey.environment());
        if (split == null) original.call(draw, buffer);
        else FlywheelShipPieces.draw(split, program.handle(), () -> original.call(draw, buffer));
    }

    @WrapOperation(method = "renderCrumbling", at = @At(value = "INVOKE", target =
        "Ldev/engine_room/flywheel/backend/engine/instancing/InstancedDraw;renderOne(Ldev/engine_room/flywheel/backend/gl/TextureBuffer;)V"))
    private void vs$drawCrumblingPieces(InstancedDraw draw, TextureBuffer buffer, Operation<Void> original, @Local GlProgram program) {
        vs$drawPieces(draw, buffer, original, program);
    }
}
