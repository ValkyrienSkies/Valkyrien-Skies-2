package org.valkyrienskies.mod.fabric.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.jellysquid.mods.sodium.client.model.color.ColorProvider;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.world.WorldSlice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.FluidState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.iris.IrisCompat;
import org.valkyrienskies.mod.compat.sodium.light.VsVertexFlagPacker;

/**
 * Pack the vertex flag bits for fluid quads on shipyard blocks. FluidRenderer
 * has its own writeGeometry-like path (updateQuad) that calls
 * {@code ColorABGR.withAlpha} per vertex; without this mixin, ship water keeps
 * the shipyard-biome water color baked at mesh time and never re-tints to the
 * world biome at the ship's actual position.
 *
 * <p>Sodium passes {@code shade=false} to the lighter for fluids, so
 * {@code br = ao} (no directional shade pre-multiplied) — we can pack the AO
 * straight through with {@code isShaded=false} so the FSH skips its
 * world-space directional shade for water.
 */
@Mixin(FluidRenderer.class)
public class MixinFluidRenderer {
    @Unique
    private boolean vs$shouldPack = false;
    @Unique
    private int vs$resolverType = 0;

    @Inject(method = "updateQuad", at = @At("HEAD"))
    private void vs$captureFluidFlags(ModelQuadView quad, WorldSlice world, BlockPos pos,
                                      LightPipeline lighter, Direction dir, float brightness,
                                      ColorProvider<FluidState> colorProvider, FluidState fluidState,
                                      CallbackInfo ci) {
        boolean anyShipFeature = VSGameConfig.CLIENT.getDynamicShipBiomeTinting()
                || VSGameConfig.CLIENT.getDynamicShipLighting();
        boolean worldFromShip = VSGameConfig.CLIENT.getDynamicShipToWorldLighting();
        if (!anyShipFeature && !worldFromShip) {
            vs$shouldPack = false;
            return;
        }
        if (LoadedMods.getIris() && IrisCompat.isIrisShaderActive()) {
            vs$shouldPack = false;
            return;
        }
        boolean inShipyard = VsVertexFlagPacker.isShipyardBlock(pos);
        // Pack shipyard fluids when ship features are on, and world fluids
        // when ship-to-world lighting is on (so the world FSH gets the
        // FACE_UNSHADED slot and skips its AO sample for water surfaces).
        vs$shouldPack = (inShipyard && anyShipFeature) || (!inShipyard && worldFromShip);
        vs$resolverType = (inShipyard && vs$shouldPack)
                ? VsVertexFlagPacker.resolverTypeFor(fluidState) : 0;
    }

    @WrapOperation(method = "updateQuad",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/api/util/ColorABGR;withAlpha(IF)I",
                    remap = false))
    private int vs$packFluidColor(int origColor, float brAndBrightness, Operation<Integer> original) {
        if (!vs$shouldPack) return original.call(origColor, brAndBrightness);
        // Fluids aren't directionally shaded by sodium (lighter.calculate is
        // called with shade=false), so brAndBrightness is ao*brightness with no
        // shade to divide out. Pack with the UNSHADED face slot so the FSH
        // doesn't add directional shade on top.
        return VsVertexFlagPacker.packColor(origColor, brAndBrightness, vs$resolverType,
                VsVertexFlagPacker.FACE_UNSHADED);
    }
}
