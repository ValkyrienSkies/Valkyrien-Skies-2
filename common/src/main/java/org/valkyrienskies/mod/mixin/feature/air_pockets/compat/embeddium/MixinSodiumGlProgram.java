package org.valkyrienskies.mod.mixin.feature.air_pockets.compat.embeddium;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCull;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCullRenderContext;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketShipWaterTintRenderContext;

@Pseudo
@Mixin(
    targets = {
        "me.jellysquid.mods.sodium.client.gl.shader.GlProgram",
    },
    remap = false
)
public abstract class MixinSodiumGlProgram {

    @Inject(method = "bind()V", at = @At("TAIL"), require = 0)
    private void valkyrienair$bindWaterCullUniforms(final CallbackInfo ci) {
        // GlProgram inherits handle() from GlObject; rather than shadowing the inherited method (which is not
        // considered part of the direct target class for @Shadow resolution), query the currently bound program.
        final int programId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (programId == 0) return;

        final boolean shipTintActive = ShipWaterPocketShipWaterTintRenderContext.isActive();
        final int shipTintRgb = shipTintActive ? ShipWaterPocketShipWaterTintRenderContext.getTintRgb() : 0xFFFFFF;

        if (ShipWaterPocketExternalWaterCullRenderContext.isInWorldFluidChunkLayer()) {
            final ClientLevel level = ShipWaterPocketExternalWaterCullRenderContext.getLevel();
            if (level != null) {
                // IMPORTANT: VS2's Sodium/Embeddium ship renderer uses the same chunk shader program but feeds it
                // ship-space camera-relative coordinates. The water-cull shader logic expects world-space cam-relative
                // coordinates, so we must fully disable culling while ships are being rendered.
                if (ShipWaterPocketExternalWaterCullRenderContext.isInShipRender()) {
                    ShipWaterPocketExternalWaterCull.disableProgram(programId);
                    ShipWaterPocketExternalWaterCull.setShipPassProgram(programId, true);
                    ShipWaterPocketExternalWaterCull.setShipWaterTintEnabledProgram(programId, shipTintActive);
                    ShipWaterPocketExternalWaterCull.setShipWaterTintProgram(programId, shipTintRgb);
                    return;
                }

                ShipWaterPocketExternalWaterCull.setupForWorldTranslucentPassProgram(programId, level,
                    ShipWaterPocketExternalWaterCullRenderContext.getCamX(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamY(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamZ());
                ShipWaterPocketExternalWaterCull.setShipPassProgram(programId, false);
                ShipWaterPocketExternalWaterCull.setShipWaterTintEnabledProgram(programId, false);
                ShipWaterPocketExternalWaterCull.setShipWaterTintProgram(programId, 0xFFFFFF);
                return;
            }
        }

        ShipWaterPocketExternalWaterCull.disableProgram(programId);
    }
}
