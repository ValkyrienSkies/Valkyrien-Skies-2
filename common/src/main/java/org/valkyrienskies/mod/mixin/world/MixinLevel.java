package org.valkyrienskies.mod.mixin.world;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;

@Mixin(Level.class)
public abstract class MixinLevel implements IShipObjectWorldProvider {
    @Inject(method = "close", at = @At("TAIL"))
    private void postClose(final CallbackInfo ci) {
        getShipObjectWorld().destroyWorld();
    }
}
