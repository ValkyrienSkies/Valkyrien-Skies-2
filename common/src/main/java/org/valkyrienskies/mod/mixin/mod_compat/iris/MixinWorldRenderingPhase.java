package org.valkyrienskies.mod.mixin.mod_compat.iris;

import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderTypes;

/**
 * Makes a shaderpack treat the world-fluid cull layer as ordinary translucent terrain.
 *
 * <p>World fluid is re-meshed into {@link ShipFluidRenderTypes#AIR_CULL_RENDER_TYPE} so the ship fluid
 * cull can drop its quads, but Iris resolves programs from the rendering phase and has never heard of
 * that render type — left alone it falls through to a phase whose program is not the water one, and the
 * layer loses the pack's water vertex shader.</p>
 *
 * <p>Reporting {@code TERRAIN_TRANSLUCENT} is the whole fix: that is the phase Iris maps to
 * {@code gbuffers_water}, which is exactly how this geometry should be shaded — it is the same world
 * fluid, drawn in a different layer.</p>
 */
@Mixin(WorldRenderingPhase.class)
public abstract class MixinWorldRenderingPhase {

    @Inject(method = "fromTerrainRenderType", at = @At("HEAD"), cancellable = true)
    private static void vs$treatFluidCullLayerAsTranslucentTerrain(final RenderType renderType,
        final CallbackInfoReturnable<WorldRenderingPhase> cir) {
        if (renderType == ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE) {
            cir.setReturnValue(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        }
    }
}
