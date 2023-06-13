package org.valkyrienskies.mod.mixin.world.level;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.common.util.DimensionIdProvider;
import org.valkyrienskies.mod.mixin.accessors.resource.ResourceKeyAccessor;

/**
 * This mixin isn't entirely necessary, but it optimizes [Level.dimensionId] in [VSGameUtils.kt] by caching this value
 * as a field in the Level class. For some reason [ResourceLocation.toString()] is ridiculously slow.
 */
@Mixin(Level.class)
public abstract class MixinLevel implements DimensionIdProvider {
    @Unique
    private String vsDimensionIdCached = null;

    @NotNull
    @Override
    public String getDimensionId() {
        if (vsDimensionIdCached == null) {
            final ResourceKey<Level> dim = dimension();
            vsDimensionIdCached =
                ((ResourceKeyAccessor) dim).getRegistryName().toString() + ":" + dim.location();
        }
        return vsDimensionIdCached;
    }

    @Shadow
    public abstract ResourceKey<Level> dimension();
}
