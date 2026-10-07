package org.valkyrienskies.mod.mixin.feature.ship_world_light.client.renderer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.render.light.ShipEntityLighting;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
    private int vs$addShipLight(final int packedLight, final Entity entity, final float partialTick) {
        return ShipEntityLighting.apply(packedLight, entity, partialTick);
    }
}
