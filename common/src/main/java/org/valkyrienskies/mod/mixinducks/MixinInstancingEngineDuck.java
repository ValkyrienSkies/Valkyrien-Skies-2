package org.valkyrienskies.mod.mixinducks;

import com.jozufozu.flywheel.backend.RenderLayer;
import org.joml.Matrix4f;

public interface MixinInstancingEngineDuck {
    void vs$render(Matrix4f viewProjection, double camX, double camY, double camZ, RenderLayer layer);
}
