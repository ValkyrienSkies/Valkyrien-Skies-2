package org.valkyrienskies.mod.mixin.feature.render_ship_debug_bb;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.debug.ShipDebugRenderer;

@Mixin(Gui.class)
public class MixinGui {
    @Inject(method = "render", at = @At("TAIL"))
    private void renderShipDebugInfo(final GuiGraphics graphics, final float partialTick, final CallbackInfo ci) {
        ShipDebugRenderer.renderHud(graphics);
    }
}
