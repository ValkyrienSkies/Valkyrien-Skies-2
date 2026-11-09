package org.valkyrienskies.mod.mixin.world.level.lighting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LightEngine.class)
public abstract class MixinLightEngineNullSafeRead {

    @WrapMethod(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I")
    private int vs$nullSafeGetLightValue(final BlockPos pos, final Operation<Integer> original) {
        try {
            return original.call(pos);
        } catch (final NullPointerException ignored) {
            return 0;
        }
    }
}
