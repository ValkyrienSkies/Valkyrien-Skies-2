package org.valkyrienskies.mod.common.util;

import java.util.function.BiFunction;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class MovementSubsteps {
    private MovementSubsteps() {
    }

    public static Vector3d resolve(final Vector3dc movement,
        final BiFunction<Vector3dc, Vector3dc, Vector3dc> collision) {
        if (!movement.isFinite()) return new Vector3d();
        final int steps = Math.max(1, Math.min(1024, (int) Math.ceil(movement.length() / 0.5)));
        final Vector3dc step = movement.div(steps, new Vector3d());
        final Vector3d offset = new Vector3d();
        for (int i = 0; i < steps; i++) {
            offset.add(collision.apply(step, offset));
        }
        return offset;
    }
}
