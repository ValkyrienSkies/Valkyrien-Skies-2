package org.valkyrienskies.mod.common.render.batched;

import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;

/**
 * A ship the batched renderer draws: a loaded ship ({@link ShipRenderObject}) or a render-only copy of a ship beyond
 * the load distance ({@link DistantShipRenderObject}). The pose getters describe the ship for the current frame.
 */
abstract class BatchedShipDrawable implements AutoCloseable {

    abstract long shipId();

    abstract ShipMesh getMesh();

    abstract boolean isEmpty();

    abstract Vector3dc renderPosition();

    abstract Quaterniondc renderRotation();

    abstract Vector3dc renderScaling();

    abstract Vector3dc renderPositionInModel();

    abstract Matrix4dc renderShipToWorld();

    abstract AABBdc renderWorldAABB();

    @Override
    public abstract void close();
}
