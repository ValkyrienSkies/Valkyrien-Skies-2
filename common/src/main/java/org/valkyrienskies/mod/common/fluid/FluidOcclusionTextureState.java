package org.valkyrienskies.mod.common.fluid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

public final class FluidOcclusionTextureState implements AutoCloseable {
    public static final int MASK_UNIT = 3;
    public static final int DESCRIPTOR_UNIT = 4;
    private final int mask;
    private final int descriptor;
    private final int maskSampler;
    private final int descriptorSampler;

    public FluidOcclusionTextureState() {
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + MASK_UNIT);
        mask = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
        maskSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + DESCRIPTOR_UNIT);
        descriptor = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        descriptorSampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GL33.glBindSampler(MASK_UNIT, 0);
        GL33.glBindSampler(DESCRIPTOR_UNIT, 0);
        GL13.glActiveTexture(active);
    }

    @Override
    public void close() {
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + MASK_UNIT);
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, GL11.glIsTexture(mask) ? mask : 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + DESCRIPTOR_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, GL11.glIsTexture(descriptor) ? descriptor : 0);
        GL33.glBindSampler(MASK_UNIT, maskSampler);
        GL33.glBindSampler(DESCRIPTOR_UNIT, descriptorSampler);
        GL13.glActiveTexture(active);
    }
}
