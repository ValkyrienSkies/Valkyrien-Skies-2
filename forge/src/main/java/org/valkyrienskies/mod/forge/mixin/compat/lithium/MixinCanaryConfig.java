package org.valkyrienskies.mod.forge.mixin.compat.lithium;

import com.abdelaziz.canary.common.config.CanaryConfig;
import com.abdelaziz.canary.common.config.Option;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CanaryConfig.class, remap = false)
public class MixinCanaryConfig {
    @Final
    @Shadow
    private Map<String, Option> options;
    @Inject(method = "applyCanaryCompat", at = @At("TAIL"), remap = false)
    private void vs$applyCanaryCompat(CallbackInfo ci) {
        options.get("mixin.ai.poi").addModOverride(false, "valkyrien-skies-2");
    }
}
