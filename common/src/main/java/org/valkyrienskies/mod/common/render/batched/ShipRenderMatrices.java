package org.valkyrienskies.mod.common.render.batched;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ShipRenderMatrices {
    private ShipRenderMatrices() { }

    /** Reuse the matrix storage. Keep the order of both calculations. */
    public static void prepare(final Matrix4dc shipToWorld, final Matrix4fc levelView,
        final double camX, final double camY, final double camZ,
        final double camShipX, final double camShipY, final double camShipZ,
        final Matrix4d scratch, final Matrix4d viewScratch,
        final Matrix4f modelView, final Matrix4f localToOrigin) {
        scratch.translation(-camX, -camY, -camZ).mul(shipToWorld)
            .translate(camShipX, camShipY, camShipZ);
        modelView.set(viewScratch.set(levelView).mul(scratch));
        scratch.translation(camX - (int) Math.floor(camX), camY - (int) Math.floor(camY),
                camZ - (int) Math.floor(camZ))
            .translate(-camX, -camY, -camZ).mul(shipToWorld)
            .translate(camShipX, camShipY, camShipZ);
        localToOrigin.set(scratch);
    }
}
