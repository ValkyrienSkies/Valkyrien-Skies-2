package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderTypes;
import org.valkyrienskies.mod.common.config.VSGameConfig;

import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.client.renderer.RenderType;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

@Mixin(DefaultMaterials.class)
public class MixinDefaultMaterials {

    @Inject(method = "forRenderLayer", at = @At("HEAD"), cancellable = true)
    private static void onForRenderLayer(RenderType layer, CallbackInfoReturnable<Material> cir) {
        if (layer == ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE
                && VSGameConfig.CLIENT.getUnderwater().getEnableWaterCulling()) {
            cir.setReturnValue(SodiumCompat.AIR_POCKET_MATERIAL);
        }
    }
}
