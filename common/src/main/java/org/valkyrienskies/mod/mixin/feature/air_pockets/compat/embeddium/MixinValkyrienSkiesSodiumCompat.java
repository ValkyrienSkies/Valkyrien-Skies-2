package org.valkyrienskies.mod.mixin.feature.air_pockets.compat.embeddium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCullRenderContext;

/**
 * VS2 renders ships by reusing the Sodium/Embeddium chunk renderer with a ship-space camera transform.
 *
 * <p>Our external-world water culling integration must be disabled during that ship render pass, otherwise the injected
 * GLSL runs with ship-space coordinates and produces incorrect results.
 */
@Pseudo
@Mixin(targets = "org.valkyrienskies.mod.compat.SodiumCompat", remap = false)
public abstract class MixinValkyrienSkiesSodiumCompat {

    @Inject(method = "vsRenderLayer", at = @At("HEAD"), require = 0)
    private static void valkyrienair$beginShipRender(final CallbackInfo ci) {
        ShipWaterPocketExternalWaterCullRenderContext.beginShipRender();
    }

    @Inject(method = "vsRenderLayer", at = @At("TAIL"), require = 0)
    private static void valkyrienair$endShipRender(final CallbackInfo ci) {
        ShipWaterPocketExternalWaterCullRenderContext.endShipRender();
    }
}
