package org.valkyrienskies.mod.fabric.mixin.feature.duplicate_keybindings;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor
    InputConstants.Key getKey();

    @Accessor
    int getClickCount();

    @Accessor
    void setClickCount(int clickCount);
}
