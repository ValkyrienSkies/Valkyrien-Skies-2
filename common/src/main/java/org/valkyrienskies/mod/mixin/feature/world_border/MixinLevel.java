package org.valkyrienskies.mod.mixin.feature.world_border;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
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
    private void setWorldBorderLevel(final CallbackInfo ci) {
        ((OfLevel) this.worldBorder).setLevel(Level.class.cast(this));
    }

}
