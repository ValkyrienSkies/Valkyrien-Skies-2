package org.valkyrienskies.mod.common.render.batched;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.mod.api.render.ShipMaterialSelection;

final class ShipMaterialUniforms {
    private static final Map<ShaderInstance, ShipMaterialUniforms> cache = new WeakHashMap<>();
    private final int effect;
    private final int params;
    private final int origin;
    private final int deferredCutout;

    private ShipMaterialUniforms(final ShaderInstance shader) {
        final int program = shader.getId();
        effect = GL20.glGetUniformLocation(program, "vs_MaterialId");
        params = GL20.glGetUniformLocation(program, "vs_MaterialParams");
        origin = GL20.glGetUniformLocation(program, "vs_MaterialOrigin");
        deferredCutout = GL20.glGetUniformLocation(program, "vs_DeferredCutout");
    }

    static ShipMaterialUniforms forShader(final ShaderInstance shader) {
        if (shader == null) return null;
        final var uniforms = cache.computeIfAbsent(shader, ShipMaterialUniforms::new);
        return uniforms.effect < 0 ? null : uniforms;
    }

    void select(final ShipMaterialSelection material) {
        GL20.glUniform1i(effect, material == null ? 0 : material.getMaterial().getIndex());
        if (material != null) {
            GL20.glUniform4f(params, material.getX(), material.getY(), material.getZ(), material.getW());
        }
    }

    void origin(final int x, final int y, final int z) {
        GL20.glUniform3f(origin, x, y, z);
    }

    void setDeferredLayer(final RenderType layer) {
        final int cutout = layer == RenderType.cutout() ? 1 : layer == RenderType.cutoutMipped() ? 2 : 0;
        GL20.glUniform1i(deferredCutout, cutout);
    }
}
