package org.valkyrienskies.mod.compat.create;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3ic;

/** Create rotates local block corners about (0.5, 0.5, 0.5). */
public final class ContraptionColliderTransform {
    private ContraptionColliderTransform() { }

    public static Vector3d pivot(Vector3dc anchor) {
        return new Vector3d(anchor).add(0.5, 0.5, 0.5);
    }

    /** VS-Core already shifts centered native voxels by half a block. */
    public static Vector3d voxelOffset() {
        return new Vector3d(-0.5, -0.5, -0.5);
    }

    public static Vector3d boxLengths(Vector3ic min, Vector3ic max) {
        return new Vector3d(max.x() - min.x() + 1.0, max.y() - min.y() + 1.0, max.z() - min.z() + 1.0);
    }
}
