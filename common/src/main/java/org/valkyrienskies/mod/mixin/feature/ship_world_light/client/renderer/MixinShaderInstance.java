package org.valkyrienskies.mod.mixin.feature.ship_world_light.client.renderer;

import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.render.light.VsShipWorldLightRenderContext;
import org.valkyrienskies.mod.common.render.light.VsShipWorldLightTerrainUniforms;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance {

    @Shadow
    private int programId;

    @Unique
    private boolean vs$swlChecked = false;

    @Unique
    private boolean vs$swlIsTerrainShader = false;

    @Inject(method = "apply()V", at = @At("TAIL"), require = 0)
    private void vs$applyShipWorldLightUniforms(final CallbackInfo ci) {
        if (!this.vs$swlChecked) {
            this.vs$swlChecked = true;
            this.vs$swlIsTerrainShader =
                GL20.glGetUniformLocation(this.programId, "u_VsShipLightCameraPos") >= 0;
        }
        if (!this.vs$swlIsTerrainShader) {
            return;
        }

        if (VsShipWorldLightRenderContext.isInWorldTerrainLayer()) {
            VsShipWorldLightTerrainUniforms.setupForProgram(this.programId,
                VsShipWorldLightRenderContext.getCamX(),
                VsShipWorldLightRenderContext.getCamY(),
                VsShipWorldLightRenderContext.getCamZ());
        } else {
            VsShipWorldLightTerrainUniforms.disableForProgram(this.programId);
        }
    }
}
