package org.valkyrienskies.mod.compat.flooding.shaders.complementary;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.valkyrienskies.mod.common.fluid.ExteriorFluidFog;

public final class ComplementaryFog {
    public static final int TEXTURE_UNIT = 13;
    public static final int WATER_FIELD_UNIT = 12;
    private static TextureTarget distance;
    private static boolean ready;

    private ComplementaryFog() { }

    public static void clear() {
        ComplementaryFluidCompat.clear();
        ready = false;
        if (distance != null) distance.destroyBuffers();
        distance = null;
    }

    public static void prepare() {
        ready = false;
        if (!ComplementaryFluidCompat.active()) return;
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
        org.lwjgl.opengl.GL33.glBindSampler(TEXTURE_UNIT, 0);
        GlStateManager._bindTexture(distance.getColorTextureId());
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "vs_ExteriorWaterPath"), TEXTURE_UNIT);
        if (GL20.glGetUniformLocation(program, "vs_ExteriorWorldWater") >= 0) {
            ExteriorFluidFog.bindWaterField(program, WATER_FIELD_UNIT);
            final var client = Minecraft.getInstance();
            final var camera = client.gameRenderer.getMainCamera().getPosition();
            org.lwjgl.opengl.GL33.glBindSampler(org.valkyrienskies.mod.common.fluid.FluidOcclusionTextureState.MASK_UNIT, 0);
            org.lwjgl.opengl.GL33.glBindSampler(org.valkyrienskies.mod.common.fluid.FluidOcclusionTextureState.DESCRIPTOR_UNIT, 0);
            org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer.setupForProgram(program, client.level, camera.x, camera.y, camera.z);
        }
        GlStateManager._activeTexture(active);
        final var minecraft = Minecraft.getInstance();
        final int color = minecraft.level.getBiome(BlockPos.containing(minecraft.gameRenderer.getMainCamera().getPosition()))
            .value().getWaterFogColor();
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "vs_ExteriorWaterColor"),
            ((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f);
    }

    public static String fragment(final String name, final String source) {
        if (!name.equals("deferred1") || !ComplementaryFluidCompat.selected()) return source;
        final String fog = "if (isEyeInWater == 1) DoWaterFog(color, lViewPos);";
        final String color = "pow(fogColor, vec3(0.33, 0.21, 0.26))";
        if (!source.contains(fog) || !source.contains("vec3 waterFogColor")) return source;
        final int header = source.indexOf('\n') + 1;
        return source.substring(0, header)
            + "uniform bool vs_ExteriorFogEnabled;\nuniform sampler2D vs_ExteriorWaterPath;\nuniform vec3 vs_ExteriorWaterColor;\n"
            + source.substring(header).replace(color,
                "pow(vs_ExteriorFogEnabled ? vs_ExteriorWaterColor : fogColor, vec3(0.33, 0.21, 0.26))")
                .replace(fog, "if (vs_ExteriorFogEnabled && !isReflection) DoWaterFog(color, texture2D(vs_ExteriorWaterPath, gl_FragCoord.xy / vec2(viewWidth, viewHeight)).r); else " + fog)
                .replace("gl_FragData[0] = vec4(color.rgb, 1.0);",
                    "if (vs_ExteriorFogEnabled && z0 == 1.0) DoWaterFog(color, texture2D(vs_ExteriorWaterPath, gl_FragCoord.xy / vec2(viewWidth, viewHeight)).r);\ngl_FragData[0] = vec4(color.rgb, 1.0);");
    }
}
