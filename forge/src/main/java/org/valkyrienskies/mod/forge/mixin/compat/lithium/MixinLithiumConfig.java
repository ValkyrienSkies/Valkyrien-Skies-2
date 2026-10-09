package org.valkyrienskies.mod.forge.mixin.compat.lithium;

import net.caffeinemc.caffeineconfig.CaffeineConfig;
import org.spongepowered.asm.mixin.Mixin;

import me.jellysquid.mods.lithium.common.config.LithiumConfig;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LithiumConfig.class, remap = false)
public class MixinLithiumConfig {
    @Inject(method = "applyLithiumCompat", at = @At("TAIL"), remap = false)
    private void vs$applyLithiumCompat(CaffeineConfig config,
        CallbackInfoReturnable<CaffeineConfig> cir) {
        config.getOption("mixin.ai.poi").addModOverride(false, "valkyrien-skies-2");
    }
}
