package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.api.render.ShipMaterialPass;
import org.valkyrienskies.mod.api.render.ShipMaterialSelection;
import org.valkyrienskies.mod.client.render.ShipMaskTerrain;
import org.valkyrienskies.mod.common.render.light.ShipShadowRenderer;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import static org.lwjgl.opengl.GL33C.*;

final class ShipMaskDraw {
    private static final int[] LIGHT_UNITS = {VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT,
        VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT, VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT, ShipShadowRenderer.TEXTURE_UNIT};
    private static int atlasSampler;

    static void draw(final ShaderInstance shader, final ShipMaskTerrain terrain, final ClientShip ship,
        final ShipMesh mesh, final ShipMaterialSelection selection) {
        final int program = glGetInteger(GL_CURRENT_PROGRAM);
        final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int active = glGetInteger(GL_ACTIVE_TEXTURE);
        final boolean needsLighting = glGetUniformLocation(shader.getId(), "Sampler2") >= 0;
        final int[] lights = new int[needsLighting ? LIGHT_UNITS.length : 0];
        for (int i = 0; i < lights.length; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + LIGHT_UNITS[i]);
            lights[i] = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        }
        GlStateManager._activeTexture(GL_TEXTURE2);
        final int lightmap = glGetInteger(GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(GL_TEXTURE0);
        final int atlas = glGetInteger(GL_TEXTURE_BINDING_2D);
        final int sampler = glGetInteger(GL_SAMPLER_BINDING);
        try {
            glBindTexture(GL_TEXTURE_2D, terrain.getAtlas());
            glBindSampler(0, atlasSampler());
            GlStateManager._activeTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, terrain.getLightmap());
            glUseProgram(shader.getId());
            if (needsLighting) {
                ShipBatchRenderer.bindLighting(shader.getId(), terrain.getX(), terrain.getY(), terrain.getZ());
            }
            drawMesh(shader, terrain, ship, mesh, selection);
        } finally {
            glBindVertexArray(vao);
            glUseProgram(program);
            for (int i = 0; i < lights.length; i++) {
                GlStateManager._activeTexture(GL_TEXTURE0 + LIGHT_UNITS[i]);
                glBindTexture(GL_TEXTURE_BUFFER, lights[i]);
            }
            GlStateManager._activeTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, lightmap);
            GlStateManager._activeTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, atlas);
            glBindSampler(0, sampler);
            GlStateManager._activeTexture(active);
        }
    }

    private static int atlasSampler() {
        if (atlasSampler == 0) {
            atlasSampler = glGenSamplers();
            glSamplerParameteri(atlasSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
            glSamplerParameteri(atlasSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        }
        return atlasSampler;
    }

    static void releaseSampler() {
        if (atlasSampler != 0) glDeleteSamplers(atlasSampler);
        atlasSampler = 0;
    }

    private static void drawMesh(final ShaderInstance shader, final ShipMaskTerrain terrain, final ClientShip ship,
        final ShipMesh mesh, final ShipMaterialSelection selection) {
        final int program = shader.getId();
        final var transform = ship.getRenderTransform();
        final var camera = new Vector3d();
        ShipBatchRenderer.cameraInShip(transform, terrain.getX(), terrain.getY(), terrain.getZ(), camera);
        final Matrix4f modelView = new Matrix4f(), localToCamera = new Matrix4f();
        ShipRenderMatrices.prepare(transform.getShipToWorld(), terrain.getModelView(), terrain.getX(), terrain.getY(),
            terrain.getZ(), camera.x, camera.y, camera.z, new Matrix4d(), new Matrix4d(), modelView, localToCamera);
        final float[] matrix = new float[16];
        glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, modelView.get(matrix));
        glUniformMatrix4fv(glGetUniformLocation(program, "vs_LocalToCameraRel"), false, localToCamera.get(matrix));
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, terrain.getProjection().get(matrix));
        glUniform4fv(glGetUniformLocation(program, "ColorModulator"), terrain.getColor());
        glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
        glUniform1i(glGetUniformLocation(program, "Sampler2"), 2);

        final var uniforms = ShipMaterialUniforms.forShader(shader);
        final boolean hasMaterial = selection != null && uniforms != null;
        if (uniforms != null) uniforms.select(selection);
        final int claimX = hasMaterial ? ship.getChunkClaim().getXStart() << 4 : 0;
        final int claimZ = hasMaterial ? ship.getChunkClaim().getZStart() << 4 : 0;
        final int alpha = glGetUniformLocation(program, "vs_MaskUsesAlpha");
        final int cutout = glGetUniformLocation(program, "vs_MaskCutout");
        final int offset = glGetUniformLocation(program, "ChunkOffset");
        glUniform1i(alpha, selection != null && selection.getPass() == ShipMaterialPass.BLENDED ? 1 : 0);
        glUniform3f(offset, (float) (mesh.refX - camera.x), (float) (mesh.refY - camera.y), (float) (mesh.refZ - camera.z));
        if (hasMaterial) uniforms.origin(mesh.refX - claimX, mesh.refY, mesh.refZ - claimZ);
        for (int i = 0; i < ShipSectionMesh.CHUNK_LAYERS.length; i++) {
            final var layer = ShipSectionMesh.CHUNK_LAYERS[i];
            final var buffer = mesh.getOpaque(i);
            if (buffer == null || layer == RenderType.tripwire() || layer == RenderType.translucent()) continue;
            glUniform1i(cutout, layer == RenderType.cutout() ? 1 : layer == RenderType.cutoutMipped() ? 2 : 0);
            buffer.bind();
            buffer.draw();
        }
        glUniform1i(alpha, 1);
        glUniform1i(cutout, 0);
        final int translucent = ShipSectionMesh.layerIndex(RenderType.translucent());
        for (final var section : mesh.translucentSections.values()) {
            final var buffer = section.getBuffer(translucent);
            if (buffer == null) continue;
            glUniform3f(offset, (float) (section.originX - camera.x), (float) (section.originY - camera.y),
                (float) (section.originZ - camera.z));
            if (hasMaterial) uniforms.origin(section.originX - claimX, section.originY, section.originZ - claimZ);
            buffer.bind();
            buffer.draw();
        }
    }
}
