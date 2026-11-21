package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.flywheel.FlywheelShipPieces;

@Mixin(value = IndirectInstancer.class, remap = false)
public class MixinIndirectInstancer {
    @Inject(method = "writeModel", at = @At("RETURN"))
    private void vs$includeSplitImages(long descriptor, CallbackInfo ci) {
        if (FlywheelShipPieces.split(((IndirectInstancer<?>) (Object) this).environment) != null) {
            MemoryUtil.memPutFloat(descriptor + 24L, -1f);
        }
    }
}
