package org.valkyrienskies.mod.mixin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class ValkyrienMixinErrorHandler implements IMixinErrorHandler {

    private final Set<String> warnList = new HashSet<>(Arrays.asList(
        "org.valkyrienskies.mod.mixin.feature.water_in_ships_entity.MixinEntity",
        "org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons.MixinAbstractCannonProjectile",
        "org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons.MixinPitchOrientedContraptionEntity"
    ));

    @Override
    public ErrorAction onPrepareError(final IMixinConfig config, final Throwable th, final IMixinInfo mixin,
        final ErrorAction action) {
        if (warnList.contains(mixin.getClassName())) {
            return ErrorAction.WARN;
        }

        return null;
    }

    @Override
    public ErrorAction onApplyError(final String targetClassName, final Throwable th, final IMixinInfo mixin,
        final ErrorAction action) {
        if (warnList.contains(mixin.getClassName())) {
            return ErrorAction.WARN;
        }

        return null;
    }
}
