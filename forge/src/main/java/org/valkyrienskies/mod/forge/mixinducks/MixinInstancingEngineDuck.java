package org.valkyrienskies.mod.forge.mixinducks;

import com.jozufozu.flywheel.backend.RenderLayer;
import com.mojang.math.Matrix4f;

public interface MixinInstancingEngineDuck {
    void render(Matrix4f viewProjection, double camX, double camY, double camZ, RenderLayer layer);
}
