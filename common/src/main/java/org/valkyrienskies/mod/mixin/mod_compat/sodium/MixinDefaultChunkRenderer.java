package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;

import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCull;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketExternalWaterCullRenderContext;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.iris.IrisCompat;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinDefaultChunkRenderer {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/ShaderChunkRenderer;begin(Lme/jellysquid/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V"), remap = false)
    private void redirectBegin(ShaderChunkRenderer instance, TerrainRenderPass renderPass, ChunkRenderMatrices matrices) {
        Matrix4f transform = SodiumCompat.popTransform();
        // Only swap in the VS ship shader when something actually needs it:
        //   - we're rendering a ship pass,
        //   - iris isn't running its own shader pack,
        //   - and at least one of the three VS shader features is enabled.
        // With all three off, the mesher mixin's gates fall through to sodium's
        // native byte format, so sodium's stock shader renders ship blocks
        // correctly. Skipping the swap also avoids compiling/binding an
        // effectively-empty program.
        boolean irisActive = LoadedMods.getIris() && IrisCompat.isIrisShaderActive();
        if (SodiumCompat.isRenderingShip() && !irisActive && SodiumCompat.anyShipShaderFeatureEnabled()) {
            renderPass.startDrawing();
            ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH, renderPass, ((ShaderChunkRendererAccessor) instance).getVertexType());
            ((ShaderChunkRendererAccessor) instance).setActiveProgram(SodiumCompat.getOrCreateShipProgram(options));
            ((ShaderChunkRendererAccessor) instance).getActiveProgram().bind();
            SodiumCompat.setupShipShaderState(((ShaderChunkRendererAccessor) instance).getActiveProgram(), matrices, transform);
            // Ship-on-ship: bind the per-frame voxel lists to the same units
            // setupShipShaderState wrote into the shader's samplers. Without
            // this the ship shader would sample whatever was last bound
            // (typically nothing on the first ship pass of the frame).
            SodiumCompat.getShipEmitterList().bind(SodiumCompat.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            SodiumCompat.getShipOccluderList().bind(SodiumCompat.SHIP_OCCLUDER_LIST_TEXTURE_UNIT);
            return;
        }
        // World chunk path: when ship-to-world dynamic lighting is on AND
        // ships are projecting voxels into the world's section grid, swap in
        // the VS world shader so ground beneath ships gets shadowed and ship
        // emitters illuminate world blocks. Same iris guard as the ship path.
        if (!SodiumCompat.isRenderingShip() && !irisActive && SodiumCompat.shouldUseWorldFromShipShader()) {
            renderPass.startDrawing();
            ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH, renderPass, ((ShaderChunkRendererAccessor) instance).getVertexType());
            ((ShaderChunkRendererAccessor) instance).setActiveProgram(SodiumCompat.getOrCreateWorldProgram(options));
            ((ShaderChunkRendererAccessor) instance).getActiveProgram().bind();
            SodiumCompat.setupWorldShaderState(((ShaderChunkRendererAccessor) instance).getActiveProgram(), matrices);
            // Bind ship-voxel storage to the units the world FSH samples.
            SodiumCompat.getShipEmitterList().bind(SodiumCompat.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            // World-from-ship storage (occluder strength) for the AO sample.
            SodiumCompat.getWorldFromShipStorage().bind(
                    SodiumCompat.WORLD_FROM_SHIP_SECTIONS_TEXTURE_UNIT,
                    SodiumCompat.WORLD_FROM_SHIP_LUT_TEXTURE_UNIT);
            // Per-frame ship occluder voxel list — used by the per-fragment
            // AO loop in the world FSH for rotation-aware shadow shape.
            SodiumCompat.getShipOccluderList().bind(SodiumCompat.SHIP_OCCLUDER_LIST_TEXTURE_UNIT);
            if (ShipWaterPocketExternalWaterCullRenderContext.isInWorldFluidChunkLayer()) {
                ShipWaterPocketExternalWaterCull.setupForWorldTranslucentPassProgram(
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    ShipWaterPocketExternalWaterCullRenderContext.getLevel(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamX(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamY(),
                    ShipWaterPocketExternalWaterCullRenderContext.getCamZ()
                );
            }
            return;
        }
        ((ShaderChunkRendererAccessor) (Object) this).invokeBegin(renderPass);
    }
}
