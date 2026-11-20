package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectDrawManager;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.flywheel.FlywheelShipPieces;

@Mixin(value = IndirectDrawManager.class, remap = false)
public class MixinIndirectDrawManager {
    @WrapOperation(method = "renderCrumbling", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL40;glDrawElementsIndirect(IIJ)V"))
    private void vs$crumblingPieces(int mode, int type, long offset, Operation<Void> original, @Local IndirectInstancer<?> instancer) {
        var split = FlywheelShipPieces.split(instancer.environment);
        if (split == null) original.call(mode, type, offset);
        else FlywheelShipPieces.draw(split, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), () -> original.call(mode, type, offset));
    }
}
