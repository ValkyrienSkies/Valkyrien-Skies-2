package org.valkyrienskies.mod.mixin.world.level;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.common.util.DimensionIdProvider;

/**
 * This mixin isn't entirely necessary, but it optimizes [Level.dimensionId] in [VSGameUtils.kt] by caching this value
 * as a field in the Level class. For some reason [ResourceLocation.toString()] is ridiculously slow.
 */
@Mixin(Level.class)
public abstract class MixinLevel implements DimensionIdProvider {
    @Unique
    private Object vsDimensionIdCached = null;

    @NotNull
    @Override
    public Object getDimensionId() {
        if (vsDimensionIdCached == null) {
            vsDimensionIdCached = dimension();
        }
        return vsDimensionIdCached;
    }

    @Shadow
    public abstract ResourceKey<Level> dimension();
}
