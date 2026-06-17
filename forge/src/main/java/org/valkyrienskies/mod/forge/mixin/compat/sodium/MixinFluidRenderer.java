package org.valkyrienskies.mod.forge.mixin.compat.sodium;

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

import org.embeddedt.embeddium.render.chunk.ChunkColorWriter;
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
 * Embeddium-on-Forge pack of fluid vertex flags. Embeddium's FluidRenderer
 * routes through {@code ChunkColorWriter.writeColor(int, float)} (interface
 * dispatch) instead of sodium's {@code ColorABGR.withAlpha} static call. The
 * containing method ({@code updateQuad}) and the surrounding logic are
 * otherwise identical to sodium's. See {@link MixinBlockRenderer} for the
 * per-block packing.
 */
@Mixin(value = FluidRenderer.class, remap = false)
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
                    target = "Lorg/embeddedt/embeddium/render/chunk/ChunkColorWriter;writeColor(IF)I",
                    remap = false))
    private int vs$packFluidColor(ChunkColorWriter writer, int origColor, float brAndBrightness, Operation<Integer> original) {
        if (!vs$shouldPack) return original.call(writer, origColor, brAndBrightness);
        // Fluids aren't directionally shaded (lighter.calculate is called with
        // shade=false), so brAndBrightness is ao*brightness with no shade to
        // divide out. Pack with the UNSHADED face slot so the FSH doesn't add
        // directional shade on top.
        return VsVertexFlagPacker.packColor(origColor, brAndBrightness, vs$resolverType,
                VsVertexFlagPacker.FACE_UNSHADED);
    }
}
