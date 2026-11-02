package org.valkyrienskies.mod.mixin.feature.air_pockets.client.renderer;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCull;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCullRenderContext;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketShipWaterTintRenderContext;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance {

    @Shadow
    private int programId;

    @Unique
    private boolean valkyrienair$checkedExternalWaterCullUniform = false;

    @Unique
    private boolean valkyrienair$hasExternalWaterCullUniform = false;

    @Inject(method = "apply()V", at = @At("TAIL"), require = 0)
    private void valkyrienair$applyExternalWorldWaterCullingUniforms(final CallbackInfo ci) {
        final ShaderInstance shader = (ShaderInstance) (Object) this;

        if (!this.valkyrienair$checkedExternalWaterCullUniform) {
            this.valkyrienair$checkedExternalWaterCullUniform = true;
            this.valkyrienair$hasExternalWaterCullUniform = shader.getUniform("ValkyrienAir_CullEnabled") != null;
        }

        if (!this.valkyrienair$hasExternalWaterCullUniform) return;

        final boolean shipTintActive = ShipWaterPocketShipWaterTintRenderContext.isActive();
        final int shipTintRgb = shipTintActive ? ShipWaterPocketShipWaterTintRenderContext.getTintRgb() : 0xFFFFFF;

        if (ShipWaterPocketExternalWaterCullRenderContext.isInWorldFluidChunkLayer()) {
            final var level = ShipWaterPocketExternalWaterCullRenderContext.getLevel();
            if (level != null) {
                // ShaderInstance.apply has already processed samplers at TAIL, so bind the water-cull textures through
                // the live program immediately instead of queueing them for a later apply.
                ShipWaterPocketExternalWaterCull.setupForWorldTranslucentPassProgram(this.programId, level,
                    ShipWaterPocketExternalWaterCullRenderContext.getCamX(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamY(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamZ());
                ShipWaterPocketExternalWaterCull.setShipPassProgram(this.programId, ShipWaterPocketExternalWaterCullRenderContext.isInShipRender());
                ShipWaterPocketExternalWaterCull.setShipWaterTintEnabledProgram(this.programId, shipTintActive);
                ShipWaterPocketExternalWaterCull.setShipWaterTintProgram(this.programId, shipTintRgb);
                return;
            }
        }

        // Ensure we don't affect other uses of patched chunk shaders outside the world fluid chunk pass,
        // but still apply dynamic ship water tint during vanilla ship rendering (which is outside the world chunk pass).
        ShipWaterPocketExternalWaterCull.disableProgram(this.programId);
        if (shipTintActive) {
            ShipWaterPocketExternalWaterCull.setShipWaterTintEnabledProgram(this.programId, true);
            ShipWaterPocketExternalWaterCull.setShipWaterTintProgram(this.programId, shipTintRgb);
        }
    }
}
