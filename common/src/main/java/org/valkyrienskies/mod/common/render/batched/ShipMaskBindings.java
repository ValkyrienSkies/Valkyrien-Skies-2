package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.platform.GlStateManager;
import org.valkyrienskies.mod.common.render.light.ShipShadowMap;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import static org.lwjgl.opengl.GL33C.*;

public final class ShipMaskBindings implements AutoCloseable {
    private static final int[] BATCH_LIGHT = {VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT,
        VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT, VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT};
    private static final int[] SODIUM_LIGHT = {6, 7, 8, 9, 12, 13};
    private final int active = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
    private final int array = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int[] atlasUnits, textures, samplers, lightUnits, lights;
    private final boolean lighting;
    private int shadow, comparison, comparisonSampler;

    public ShipMaskBindings(final boolean sodium, final boolean lighting) {
        this.lighting = lighting;
        atlasUnits = sodium ? new int[] {0, 1, 2} : new int[] {0, 2};
        textures = new int[atlasUnits.length];
        samplers = new int[textures.length];
        for (int i = 0; i < textures.length; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + atlasUnits[i]);
            textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            samplers[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        lightUnits = sodium ? SODIUM_LIGHT : BATCH_LIGHT;
        lights = new int[lighting ? lightUnits.length : 0];
        for (int i = 0; i < lights.length; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + lightUnits[i]);
            lights[i] = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        }
        if (lighting) {
            GlStateManager._activeTexture(GL_TEXTURE0 + ShipShadowMap.TEXTURE_UNIT);
            shadow = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
            GlStateManager._activeTexture(GL_TEXTURE0 + ShipShadowMap.COMPARISON_TEXTURE_UNIT);
            comparison = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
            comparisonSampler = glGetInteger(GL_SAMPLER_BINDING);
        }
        GlStateManager._activeTexture(GL_TEXTURE0);
    }

    @Override
    public void close() {
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, array);
        glUseProgram(program);
        for (int i = 0; i < lights.length; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + lightUnits[i]);
            glBindTexture(GL_TEXTURE_BUFFER, lights[i]);
        }
        if (lighting) {
            GlStateManager._activeTexture(GL_TEXTURE0 + ShipShadowMap.TEXTURE_UNIT);
            glBindTexture(GL_TEXTURE_2D_ARRAY, shadow);
            GlStateManager._activeTexture(GL_TEXTURE0 + ShipShadowMap.COMPARISON_TEXTURE_UNIT);
            glBindTexture(GL_TEXTURE_2D_ARRAY, comparison);
            glBindSampler(ShipShadowMap.COMPARISON_TEXTURE_UNIT, comparisonSampler);
        }
        for (int i = 0; i < textures.length; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + atlasUnits[i]);
            GlStateManager._bindTexture(textures[i]);
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glBindSampler(atlasUnits[i], samplers[i]);
        }
        GlStateManager._activeTexture(active);
    }
}
