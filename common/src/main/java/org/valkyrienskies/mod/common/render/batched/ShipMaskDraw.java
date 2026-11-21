package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipMaterialStack;
import org.valkyrienskies.mod.client.render.ShipMaskTerrain;
import static org.lwjgl.opengl.GL33C.*;

final class ShipMaskDraw {
    private static int atlasSampler;

    static void draw(final ShaderInstance shader, final ShipMaskTerrain terrain, final ClientShip ship,
        final ShipMesh mesh, final ShipMaterialStack materials) {
        final boolean needsLighting = glGetUniformLocation(shader.getId(), "Sampler2") >= 0;
        try (var bindings = new ShipMaskBindings(false, needsLighting)) {
            glBindTexture(GL_TEXTURE_2D, terrain.getAtlas());
            glBindSampler(0, atlasSampler());
            GlStateManager._activeTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, terrain.getLightmap());
            glUseProgram(shader.getId());
            if (needsLighting) {
                ShipBatchRenderer.bindLighting(shader.getId(), terrain.getX(), terrain.getY(), terrain.getZ());
            }
            final var uniforms = ShipMaterialUniforms.forShader(shader);
            final int previous = uniforms == null ? -1 : uniforms.bindTable(materials == null ? null : materials.getTable());
            try {
                drawMesh(shader, terrain, ship, mesh, materials, uniforms);
            } finally {
                ShipMaterialUniforms.restoreTable(previous);
            }
        }
    }

    static int atlasSampler() {
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
        final ShipMesh mesh, final ShipMaterialStack materials, final ShipMaterialUniforms uniforms) {
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

        final boolean hasMaterial = materials != null && uniforms != null;
        if (uniforms != null) uniforms.select(materials);
        final int claimX = hasMaterial ? ship.getChunkClaim().getXStart() << 4 : 0;
        final int claimZ = hasMaterial ? ship.getChunkClaim().getZStart() << 4 : 0;
        final int alpha = glGetUniformLocation(program, "vs_MaskUsesAlpha");
        final int cutout = glGetUniformLocation(program, "vs_MaskCutout");
        final int offset = glGetUniformLocation(program, "ChunkOffset");
        glUniform1i(alpha, materials != null && materials.getBlended() ? 1 : 0);
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
