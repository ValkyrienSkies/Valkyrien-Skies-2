package org.valkyrienskies.mod.fabric.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.sodium.light.VsVertexFlagPacker;

/**
 * Sodium-on-Fabric flavor of the chunk-vertex flag packer. Sodium 0.5.13 calls
 * {@code ColorABGR.withAlpha(int, float)} directly inside writeGeometry — that
 * static call is the redirect target. See
 * {@link VsVertexFlagPacker} for the packing contract.
 */
@Mixin(BlockRenderer.class)
public class MixinBlockRenderer {
    @Unique
    private boolean vs$shouldPack = false;
    @Unique
    private int vs$resolverType = 0;
    @Unique
    private int vs$faceSlot = VsVertexFlagPacker.FACE_UNSHADED;
    @Unique
    private float vs$shadeFactor = 1.0f;

    @Inject(method = "writeGeometry", at = @At("HEAD"))
    private void vs$captureFlags(BlockRenderContext ctx, ChunkModelBuilder builder, Vec3 origin,
                                 Material material, BakedQuadView quad, int[] colors, QuadLightData light,
                                 CallbackInfo ci) {
        boolean anyShipFeature = VSGameConfig.CLIENT.getDynamicShipBiomeTinting()
                || VSGameConfig.CLIENT.getDynamicShipLighting()
                || VSGameConfig.CLIENT.getBetterVanillaShipShading();
        boolean worldFromShip = VSGameConfig.CLIENT.getDynamicShipToWorldLighting();
        if (!anyShipFeature && !worldFromShip) {
            vs$shouldPack = false;
            return;
        }
        boolean inShipyard = VsVertexFlagPacker.isShipyardBlock(ctx.pos());
        // Pack for shipyard blocks when any ship feature is on, and for world
        // blocks when ship-to-world lighting is on (the world FSH decodes the
        // face slot to get an exact world normal for AO sampling).
        vs$shouldPack = (inShipyard && anyShipFeature) || (!inShipyard && worldFromShip);
        if (!vs$shouldPack) return;
        // World blocks always get resolverType=0 — biome tinting on world
        // chunks goes through sodium's stock BlockColors path (already baked
        // into the per-vertex RGB), no shipyard biome remapping.
        vs$resolverType = (inShipyard && quad.hasColor())
                ? VsVertexFlagPacker.resolverTypeFor(ctx.state())
                : 0;
        boolean isShaded = quad.hasShade();
        boolean emissive = VsVertexFlagPacker.isEmissiveQuad((BakedQuad) quad);
        vs$faceSlot = emissive
                ? VsVertexFlagPacker.FACE_FULLBRIGHT
                : VsVertexFlagPacker.faceSlot(quad.getLightFace(), isShaded);
        vs$shadeFactor = VsVertexFlagPacker.standardShade(quad.getLightFace(), isShaded);
    }

    @WrapOperation(method = "writeGeometry",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/api/util/ColorABGR;withAlpha(IF)I",
                    remap = false))
    private int vs$packVertexColor(int origColor, float br, Operation<Integer> original) {
        if (!vs$shouldPack) return original.call(origColor, br);
        // Divide out the directional shade sodium pre-multiplied so the FSH
        // can re-apply it from the actual world-space orientation (ship: via
        // u_TransformMatrix; world: identity, the face slot already maps
        // directly to a world-space normal). Ensures no double-darkening.
        float ao = vs$shadeFactor > 1e-6f ? br / vs$shadeFactor : br;
        return VsVertexFlagPacker.packColor(origColor, ao, vs$resolverType, vs$faceSlot);
    }
}
