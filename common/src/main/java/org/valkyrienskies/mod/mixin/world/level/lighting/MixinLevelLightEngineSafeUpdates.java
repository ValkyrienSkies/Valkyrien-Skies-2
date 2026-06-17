package org.valkyrienskies.mod.mixin.world.level.lighting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelLightEngine.class)
public abstract class MixinLevelLightEngineSafeUpdates {

    @WrapMethod(method = "runLightUpdates()I")
    private int vs$safeRunLightUpdates(final Operation<Integer> original) {
        try {
            return original.call();
        } catch (final NullPointerException ignored) {
            return 0;
        }
    }
}
