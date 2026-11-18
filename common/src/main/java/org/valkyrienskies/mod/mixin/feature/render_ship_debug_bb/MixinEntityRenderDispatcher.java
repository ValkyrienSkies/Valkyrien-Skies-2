package org.valkyrienskies.mod.mixin.feature.render_ship_debug_bb;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.debug.ShipHitboxRenderer;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Inject(method = "setRenderHitBoxes", at = @At("RETURN"))
    private void vs$toggleShipHitboxes(final boolean enabled, final CallbackInfo ci) {
        ShipHitboxRenderer.setEnabled(enabled);
    }
}
