package org.valkyrienskies.mod.compat.sodium;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionTextureState;

public final class SodiumFluidOcclusion {
    private static FluidOcclusionTextureState textures;

    private SodiumFluidOcclusion() { }

    public static void begin() {
        final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (!FluidOcclusionRenderer.supportsProgram(program)) return;
        final var level = ShipRenderViews.getRenderLevel();
        if (SodiumCompat.isRenderingShip() || level == null) {
            FluidOcclusionRenderer.disableForProgram(program);
            return;
        }
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        textures = new FluidOcclusionTextureState();
        final var camera = ShipRenderViews.getCameraPosition();
        try {
            FluidOcclusionRenderer.setupForProgram(program, level, camera.x, camera.y, camera.z);
        } catch (final RuntimeException exception) {
            end();
            throw exception;
        } finally {
            GlStateManager._activeTexture(active);
        }
    }

    public static void end() {
        if (textures == null) return;
        textures.close();
        textures = null;
    }
}
