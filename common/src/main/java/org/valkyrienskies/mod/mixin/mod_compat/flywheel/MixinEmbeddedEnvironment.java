package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.ShipVisualEmbedding;

@Mixin(value = EmbeddedEnvironment.class, remap = false)
public abstract class MixinEmbeddedEnvironment implements ShipVisualEmbedding {
    @Unique private ClientShip vs$ship;

    @Override
    public void vs$setShip(ClientShip ship) {
        this.vs$ship = ship;
    }

    @Inject(method = "composeMatrices", at = @At("RETURN"))
    private void vs$applyShipVisibility(Matrix4f pose, Matrix3f normal, CallbackInfo ci) {
        if (vs$ship != null && !ShipRenderViews.shouldRenderShip(vs$ship)) {
            pose.scale(0.0f);
        }
    }
}
