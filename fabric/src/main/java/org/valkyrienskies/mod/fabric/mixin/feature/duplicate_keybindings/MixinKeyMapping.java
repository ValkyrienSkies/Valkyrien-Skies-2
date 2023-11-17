package org.valkyrienskies.mod.fabric.mixin.feature.duplicate_keybindings;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSKeyBindings;

/**
 * This Mixin makes it so VS2 keybindings are pressed even when there exists another keybinding bound to the set same
 * key.
 */
@Mixin(KeyMapping.class)
public class MixinKeyMapping {
    @Unique
    private static final Map<Key, KeyMapping> VS2_KEYMAP = Maps.newHashMap();

    @Shadow
    @Final
    private static Map<InputConstants.Key, KeyMapping> MAP;

    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Shadow
    private InputConstants.Key key;

    @Inject(method = "click", at = @At("HEAD"))
    private static void preClick(final InputConstants.Key key, final CallbackInfo callbackInfo) {
        final KeyMapping originalKeyMapping = MAP.get(key);
        final KeyMapping vs2KeyMapping = VS2_KEYMAP.get(key);
        if (vs2KeyMapping != null && originalKeyMapping != vs2KeyMapping) {
            final KeyMappingAccessor keyMappingAccessor = (KeyMappingAccessor) vs2KeyMapping;
            keyMappingAccessor.setClickCount(keyMappingAccessor.getClickCount() + 1);
        }
    }

    @Inject(method = "set", at = @At("HEAD"))
    private static void preSet(final InputConstants.Key key, final boolean bl, final CallbackInfo callbackInfo) {
        final KeyMapping originalKeyMapping = MAP.get(key);
        final KeyMapping vs2KeyMapping = VS2_KEYMAP.get(key);
        if (vs2KeyMapping != null && originalKeyMapping != vs2KeyMapping) {
            vs2KeyMapping.setDown(bl);
        }
    }

    @Inject(method = "resetMapping", at = @At("HEAD"))
    private static void preResetMapping(final CallbackInfo callbackInfo) {
        VS2_KEYMAP.clear();
        for (final KeyMapping keyMapping : ALL.values()) {
            if (VSKeyBindings.INSTANCE.isKeyMappingFromVS2(keyMapping)) {
                final KeyMappingAccessor keyMappingAccessor = (KeyMappingAccessor) keyMapping;
                VS2_KEYMAP.put(keyMappingAccessor.getKey(), keyMapping);
            }
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void postInit(final CallbackInfo callbackInfo) {
        final KeyMapping thisAsKeyMapping = KeyMapping.class.cast(this);
        if (VSKeyBindings.INSTANCE.isKeyMappingFromVS2(thisAsKeyMapping)) {
            VS2_KEYMAP.put(this.key, thisAsKeyMapping);
        }
    }
}
