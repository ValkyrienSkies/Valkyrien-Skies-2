package org.valkyrienskies.mod.common.fluid;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.flooding.shaders.ShaderFluidFog;
import org.valkyrienskies.mod.compat.iris.IrisCompat;

public final class ExteriorFluidFog {
    private static final ExteriorFluidField FIELD = new ExteriorFluidField();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static ShaderInstance shader;
    private static int depthTexture;
    private static int depthWidth, depthHeight;
    private static int vertexArray;
    private static boolean framePending;
    private static boolean distanceOnly;
    private static boolean distanceWritten;

    private ExteriorFluidFog() { }

    public static void setShader(final ShaderInstance value) { clear(); shader = value; }

    public static void beginFrame(final Matrix4f projection, final Matrix4f view) {
        INVERSE.set(projection).mul(view).invert();
        framePending = true;
    }

    public static void dirtySection(final int x, final int y, final int z) { FIELD.dirty(x, y, z); }

    public static void beforeTranslucency() {
        if (!framePending || shader == null || !VSGameConfig.CLIENT.getRenderFloodedFluidFog()
            || !VSGameConfig.CLIENT.getRenderFluidOcclusion()) return;
        if (LoadedMods.getIris() && IrisCompat.isIrisShaderActive()) return;
        final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (draw != 0) { render(); return; }
        final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, Minecraft.getInstance().getMainRenderTarget().frameBufferId);
        try { render(); }
        finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    public static void clear() {
        if (LoadedMods.getIris()) ShaderFluidFog.clear();
        FIELD.close();
        if (depthTexture != 0) GlStateManager._deleteTexture(depthTexture);
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray);
        depthTexture = vertexArray = 0;
        depthWidth = depthHeight = 0;
        framePending = false;
    }

    public static void renderToShaderTarget(final int framebuffer, final int width, final int height) {
        final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        try {
            render(framebuffer, width, height);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    public static boolean renderDistance(final int framebuffer, final int width, final int height) {
        distanceOnly = true;
        distanceWritten = false;
        try { renderToShaderTarget(framebuffer, width, height); return distanceWritten; }
        finally { distanceOnly = false; }
    }

    public static void bindWaterField(final int program, final int unit) {
        final var minimum = FIELD.minimum();
        final var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL33.glBindSampler(unit, 0);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, FIELD.texture());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "vs_ExteriorWorldWater"), unit);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "vs_WaterCamera"),
            (float) (camera.x - minimum.getX() * 16.0), (float) (camera.y - minimum.getY() * 16.0),
            (float) (camera.z - minimum.getZ() * 16.0));
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "vs_WaterOffset"),
            Math.floorMod(minimum.getX() * 16, ExteriorFluidField.SIZE),
            Math.floorMod(minimum.getY() * 16, ExteriorFluidField.SIZE),
            Math.floorMod(minimum.getZ() * 16, ExteriorFluidField.SIZE));
    }

    private static void copyDepth(final com.mojang.blaze3d.pipeline.RenderTarget target) {
        if (depthTexture == 0) depthTexture = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(depthTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        if (depthWidth != target.width || depthHeight != target.height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, target.width, target.height,
                0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
            depthWidth = target.width; depthHeight = target.height;
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, target.width, target.height);
    }

    public static void render() {
        if (LoadedMods.getIris() && IrisCompat.isIrisShaderActive()) return;
        final var target = Minecraft.getInstance().getMainRenderTarget();
        render(target.frameBufferId, target.width, target.height);
    }

    private static void render(final int framebuffer, final int width, final int height) {
        if (!framePending || shader == null || !VSGameConfig.CLIENT.getRenderFloodedFluidFog()
            || !VSGameConfig.CLIENT.getRenderFluidOcclusion()) return;
        final var minecraft = Minecraft.getInstance();
        final var level = minecraft.level;
        if (level == null) { clear(); return; }
        final var target = minecraft.getMainRenderTarget();
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != framebuffer) return;
        framePending = false;
        final var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        final var position = BlockPos.containing(camera);
        final var ambient = level.getFluidState(position);
        if (!ambient.is(FluidTags.WATER) || camera.y - position.getY() >= ambient.getHeight(level, position)
            || !CameraFluidState.sample(level, camera, ambient, true).dryDomain()) return;
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        final ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        final int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int sampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        final int texture1 = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        final int sampler1 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        try (final var masks = new FluidOcclusionTextureState()) {
            GL33.glBindSampler(0, 0); GL33.glBindSampler(1, 0);
            if (!FIELD.update(level, position)) return;
            if (vertexArray == 0) vertexArray = GL30.glGenVertexArrays();
            copyDepth(target);
            shader.setSampler("SceneDepth", depthTexture);
            shader.safeGetUniform("InverseViewProjection").set(INVERSE);
            final var minimum = FIELD.minimum();
            shader.safeGetUniform("WaterGridOffset").set((float) Math.floorMod(minimum.getX() * 16, ExteriorFluidField.SIZE),
                (float) Math.floorMod(minimum.getY() * 16, ExteriorFluidField.SIZE),
                (float) Math.floorMod(minimum.getZ() * 16, ExteriorFluidField.SIZE));
            shader.safeGetUniform("CameraInWaterGrid").set((float) (camera.x - minimum.getX() * 16.0),
                (float) (camera.y - minimum.getY() * 16.0), (float) (camera.z - minimum.getZ() * 16.0));
            final int color = level.getBiome(position).value().getWaterFogColor();
            shader.safeGetUniform("WaterFogColor").set(((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F);
            shader.safeGetUniform("DistanceOnly").set(distanceOnly ? 1 : 0);
            shader.apply();
            if (LoadedMods.getIris() && IrisCompat.isIrisShaderActive()) IrisCompat.unlockDepthColor();
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, FIELD.texture());
            GL20.glUniform1i(GL20.glGetUniformLocation(shader.getId(), "WorldWater"), 1);
            FluidOcclusionRenderer.setupForProgram(shader.getId(), level, camera.x, camera.y, camera.z);
            RenderSystem.enableBlend();
            if (distanceOnly) RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, false);
            RenderSystem.viewport(0, 0, width, height);
            GL30.glBindVertexArray(vertexArray);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            distanceWritten = distanceOnly;
            shader.clear();
        } finally {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(texture0);
            GL33.glBindSampler(0, sampler0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1); GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture1);
            GL33.glBindSampler(1, sampler1);
            GlStateManager._activeTexture(active);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindVertexArray(previousArray);
            GlStateManager._glUseProgram(previousProgram);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
            RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        }
    }
}
