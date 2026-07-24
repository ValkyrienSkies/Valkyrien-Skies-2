package org.valkyrienskies.mod.compat.sodium.light;

import org.lwjgl.opengl.GL30C;

import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniform;

public class GlUniformInt3v extends GlUniform<int[]> {
    public GlUniformInt3v(int index) {
        super(index);
    }

    @Override
    public void set(int[] value) {
        GL30C.glUniform3iv(this.index, value);
    }

    public void set(int x, int y, int z) {
        GL30C.glUniform3i(this.index, x, y, z);
    }
}
