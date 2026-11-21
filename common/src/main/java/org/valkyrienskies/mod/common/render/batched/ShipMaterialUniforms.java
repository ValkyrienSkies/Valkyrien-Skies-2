package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.valkyrienskies.mod.client.render.ShipMaterialStack;
import org.valkyrienskies.mod.client.render.ShipMaterialTable;

public final class ShipMaterialUniforms {
    private static final Map<ShaderInstance, ShipMaterialUniforms> cache = new WeakHashMap<>();
    public static final int TERRAIN_TEXTURE_UNIT = 4;
    private static final int TEXTURE_UNIT = 5;
    private static int texture;
    private static int buffer;
    private static ShipMaterialTable uploaded;
    private final int stackRange;
    private final int sampler;
    private final int origin;
    private final int deferredCutout;
    private final int disableShade;

    public ShipMaterialUniforms(final int program) {
        stackRange = GL20.glGetUniformLocation(program, "vs_MaterialRange");
        sampler = GL20.glGetUniformLocation(program, "vs_MaterialData");
        origin = GL20.glGetUniformLocation(program, "vs_MaterialOrigin");
        disableShade = GL20.glGetUniformLocation(program, "vs_DisableShipShade");
        deferredCutout = GL20.glGetUniformLocation(program, "vs_DeferredCutout");
    }

    public static ShipMaterialUniforms forShader(final ShaderInstance shader) {
        if (shader == null) return null;
        final var uniforms = cache.computeIfAbsent(shader, key -> new ShipMaterialUniforms(key.getId()));
        return uniforms.stackRange < 0 ? null : uniforms;
    }

    int bindTable(final ShipMaterialTable table) {
        return bindTable(table, TEXTURE_UNIT);
    }

    public int bindTable(final ShipMaterialTable table, final int unit) {
        GL20.glUniform1i(sampler, unit);
        if (table == null) return -1;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        final int previousTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        if (texture == 0) {
            texture = GL11.glGenTextures();
            buffer = GL15.glGenBuffers();
        }
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        if (uploaded != table) {
            final int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, table.getTexels(), GL15.GL_STREAM_DRAW);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previous);
            uploaded = table;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        return previousTexture;
    }

    public void select(final ShipMaterialStack stack) {
        if (stack == null) GL20.glUniform2i(stackRange, 0, 0);
        else GL20.glUniform2i(stackRange, stack.getOffset(), stack.getCount());
    }

    static void restoreTable(final int previousTexture) {
        restoreTable(previousTexture, TEXTURE_UNIT);
    }

    public static void restoreTable(final int previousTexture, final int unit) {
        if (previousTexture < 0) return;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, previousTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    static void release() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        if (buffer != 0) GL15.glDeleteBuffers(buffer);
        texture = buffer = 0;
        uploaded = null;
    }

    public void origin(final float x, final float y, final float z) {
        GL20.glUniform3f(origin, x, y, z);
    }

    public void setShipShade(final boolean enabled) {
        GL20.glUniform1i(disableShade, enabled ? 0 : 1);
    }

    public void setDeferredLayer(final RenderType layer) {
        final int cutout = layer == RenderType.cutout() ? 1 : layer == RenderType.cutoutMipped() ? 2 : 0;
        GL20.glUniform1i(deferredCutout, cutout);
    }
}
