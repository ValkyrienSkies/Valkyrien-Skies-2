package org.valkyrienskies.mod.mixin.feature.world_border;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(Level.class)
public class MixinLevel {

    @Shadow
    @Final
    private WorldBorder worldBorder;

    @Inject(
        at = @At("RETURN"),
        method = "<init>"
    )
    private void setWorldBorderLevel(
        final WritableLevelData writableLevelData, final ResourceKey<Level> resourceKey,
        final Holder<DimensionType> holder,
        final Supplier<ProfilerFiller> supplier, final boolean bl, final boolean bl2, final long l, final int i,
        final CallbackInfo ci) {

        ((OfLevel) this.worldBorder).setLevel(Level.class.cast(this));
    }

}
