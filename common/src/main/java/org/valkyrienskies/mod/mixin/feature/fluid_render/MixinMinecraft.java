package org.valkyrienskies.mod.mixin.feature.fluid_render;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderSnapshot;
import org.valkyrienskies.mod.common.fluid.client.ShipInteriorFogRenderer;
import org.valkyrienskies.mod.common.fluid.client.ShipPocketWorldWaterOccluder;
import org.valkyrienskies.mod.common.fluid.client.ShipWaterPocketExternalWaterCull;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    /** Ship-keyed render state outlives nothing: drop all of it when the level goes away. */
    @Inject(method = "clearLevel", at = @At("TAIL"))
    private void vs$clearShipFluidRenderState(final CallbackInfo ci) {
        ShipWaterPocketExternalWaterCull.clear();
        ShipInteriorFogRenderer.clear();
        ShipPocketWorldWaterOccluder.clear();
        ShipFluidRenderSnapshot.clear();
    }
}
