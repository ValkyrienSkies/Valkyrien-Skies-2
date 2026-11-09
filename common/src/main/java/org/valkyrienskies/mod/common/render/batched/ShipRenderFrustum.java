package org.valkyrienskies.mod.common.render.batched;

import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

/** Build a world-space view test from the active terrain matrices. */
public final class ShipRenderFrustum {
    private ShipRenderFrustum() { }

    public static Frustum create(final Matrix4f view, final Matrix4f projection,
        final double cameraX, final double cameraY, final double cameraZ) {
        final Frustum frustum = new Frustum(view, projection);
        frustum.prepare(cameraX, cameraY, cameraZ);
        return frustum;
    }
}
