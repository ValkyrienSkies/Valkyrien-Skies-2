package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipMaskTerrain;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.render.light.VsShipWorldLightTerrainUniforms;
import org.valkyrienskies.mod.mixin.accessors.client.render.LightTextureAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer.RenderChunkInfoAccessor;
import static org.lwjgl.opengl.GL33C.*;

public final class VanillaShipMask {
    private VanillaShipMask() { }

    public static void draw(final ShaderInstance shader, final ShipMaskTerrain terrain,
                            final ClientShip ship, final Iterable<RenderChunkInfo> sections) {
        final var transform = ship.getRenderTransform();
        final var camera = transform.getWorldToShip().transformPosition(new Vector3d(terrain.getX(), terrain.getY(), terrain.getZ()));
        final var modelView = new Matrix4f(terrain.getModelView());
        VSClientGameUtils.transformRenderWithShip(transform, modelView, camera.x, camera.y, camera.z,
            terrain.getX(), terrain.getY(), terrain.getZ());

        final int program = shader.getId();
        final var materials = ShipRenderViews.materialsFor(ship);
        final var uniforms = ShipMaterialUniforms.forShader(shader);
        final boolean lit = glGetUniformLocation(program, "u_VsShipLightCameraPos") >= 0;
        try (var bindings = new ShipMaskBindings(false, lit)) {
            final var minecraft = Minecraft.getInstance();
            glBindTexture(GL_TEXTURE_2D, minecraft.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getId());
            glBindSampler(0, ShipMaskDraw.atlasSampler());
            GlStateManager._activeTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, ((LightTextureAccessor) minecraft.gameRenderer.lightTexture()).vs$getLightTexture().getId());
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
            glUniform1i(glGetUniformLocation(program, "Sampler2"), 2);
            glUniform1i(glGetUniformLocation(program, "vs_DisableShipShade"), VSGameConfig.CLIENT.getBetterVanillaShipShading() ? 0 : 1);
            glUniform1f(glGetUniformLocation(program, "u_VsShipShadeStrength"), (float) VSGameConfig.CLIENT.getShipShadeStrength());
            if (lit) VsShipWorldLightTerrainUniforms.setupForProgram(program, terrain.getX(), terrain.getY(), terrain.getZ());
            final float[] matrix = new float[16];
            glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, modelView.get(matrix));
            glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, terrain.getProjection().get(matrix));
            glUniformMatrix3fv(glGetUniformLocation(program, "IViewRotMat"), false, RenderSystem.getInverseViewRotationMatrix().get(new float[9]));
            glUniform4fv(glGetUniformLocation(program, "ColorModulator"), terrain.getColor());
            final int unit = ShipMaterialUniforms.TERRAIN_TEXTURE_UNIT;
            final int previous = uniforms == null ? -1 : uniforms.bindTable(materials == null ? null : materials.getTable(), unit);
            try {
                if (uniforms != null) uniforms.select(materials);
                final int offset = glGetUniformLocation(program, "ChunkOffset");
                final int alpha = glGetUniformLocation(program, "vs_MaskUsesAlpha");
                final int cutout = glGetUniformLocation(program, "vs_MaskCutout");
                final int claimX = ship.getChunkClaim().getXStart() << 4;
                final int claimZ = ship.getChunkClaim().getZStart() << 4;
                for (final RenderType layer : RenderType.chunkBufferLayers()) {
                    if (layer == RenderType.tripwire()) continue;
                    glUniform1i(alpha, layer == RenderType.translucent() || (materials != null && materials.getBlended()) ? 1 : 0);
                    glUniform1i(cutout, layer == RenderType.cutout() ? 1 : layer == RenderType.cutoutMipped() ? 2 : 0);
                    for (final var info : sections) {
                        final var section = ((RenderChunkInfoAccessor) info).getChunk();
                        if (section.getCompiledChunk().isEmpty(layer)) continue;
                        final var origin = section.getOrigin();
                        glUniform3f(offset, (float) (origin.getX() - camera.x), (float) (origin.getY() - camera.y),
                            (float) (origin.getZ() - camera.z));
                        if (uniforms != null) uniforms.origin(origin.getX() - claimX, origin.getY(), origin.getZ() - claimZ);
                        final var buffer = section.getBuffer(layer);
                        buffer.bind();
                        buffer.draw();
                    }
                }
            } finally {
                ShipMaterialUniforms.restoreTable(previous, unit);
            }
        }
    }
}
