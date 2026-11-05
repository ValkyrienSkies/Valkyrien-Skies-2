package org.valkyrienskies.mod.compat.flooding.shaders;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.valkyrienskies.mod.common.fluid.ExteriorFluidFog;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionTextureState;

public final class ShaderFluidFog {
    public static final int TEXTURE_UNIT = 13;
    public static final int WATER_FIELD_UNIT = 12;
    private static TextureTarget distance;
    private static boolean ready;

    private ShaderFluidFog() { }

    public static void clear() {
        NativeFluidCompat.clear();
        ready = false;
        if (distance != null) distance.destroyBuffers();
        distance = null;
    }

    public static void prepare() {
        ready = false;
        if (!NativeFluidCompat.active()) return;
        final var target = Minecraft.getInstance().getMainRenderTarget();
        final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (distance == null || distance.width != target.width || distance.height != target.height) {
                clear();
                distance = new TextureTarget(target.width, target.height, false, Minecraft.ON_OSX);
                GlStateManager._bindTexture(distance.getColorTextureId());
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, target.width, target.height,
                    0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
            }
            ready = ExteriorFluidFog.renderDistance(distance.frameBufferId, distance.width, distance.height);
        } finally {
            GlStateManager._activeTexture(active);
            GlStateManager._bindTexture(texture);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    public static void bind(final int program) {
        final int enabled = GL20.glGetUniformLocation(program, "vs_ExteriorFogEnabled");
        if (enabled < 0) return;
        GL20.glUniform1i(enabled, ready ? 1 : 0);
        if (!ready) return;
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + TEXTURE_UNIT);
        GL33.glBindSampler(TEXTURE_UNIT, 0);
        GlStateManager._bindTexture(distance.getColorTextureId());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "vs_ExteriorWaterPath"), TEXTURE_UNIT);
        if (GL20.glGetUniformLocation(program, "vs_ExteriorWorldWater") >= 0) {
            ExteriorFluidFog.bindWaterField(program, WATER_FIELD_UNIT);
            final var client = Minecraft.getInstance();
            final var camera = client.gameRenderer.getMainCamera().getPosition();
            GL33.glBindSampler(FluidOcclusionTextureState.MASK_UNIT, 0);
            GL33.glBindSampler(FluidOcclusionTextureState.DESCRIPTOR_UNIT, 0);
            FluidOcclusionRenderer.setupForProgram(program, client.level, camera.x, camera.y, camera.z);
        }
        GlStateManager._activeTexture(active);
        final int waterColor = GL20.glGetUniformLocation(program, "vs_ExteriorWaterColor");
        if (waterColor < 0) return;
        final var minecraft = Minecraft.getInstance();
        final int color = minecraft.level.getBiome(BlockPos.containing(minecraft.gameRenderer.getMainCamera().getPosition()))
            .value().getWaterFogColor();
        GL20.glUniform3f(waterColor,
            ((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f);
    }
}
