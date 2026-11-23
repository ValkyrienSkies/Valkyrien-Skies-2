package org.valkyrienskies.mod.mixin.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.CoreConfigMigration;

/**
 * Migrates an old vs-core server config before the spec checks it. Forge and Forge Config API Port both hand every
 * loaded config file to {@code setConfig}, which corrects (and saves) whatever doesn't fit the spec.
 */
@Mixin(value = ForgeConfigSpec.class, remap = false)
public class MixinForgeConfigSpec {
    @Inject(method = "setConfig", at = @At("HEAD"))
    private void migrateCoreServerConfig(final CommentedConfig config, final CallbackInfo ci) {
        if (config != null) {
            CoreConfigMigration.beforeSpecAccepts(this, config);
        }
    }
}
