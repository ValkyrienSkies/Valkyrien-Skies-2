package org.valkyrienskies.mod.common.util;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementSubstepsTest {
    @Test
    void unobstructedMovementIsExact() {
        for (double speed : new double[] {0.7, 1.1, 3.7, 8.3}) {
            Vector3d movement = new Vector3d(speed, -0.78, 0.13);
            assertEquals(movement, MovementSubsteps.resolve(movement, (step, offset) -> step));
        }
    }

    @Test
    void sideCollisionPreservesFallingVelocity() {
        Vector3d movement = new Vector3d(3.7, -0.78, 0.13);
        Vector3d result = MovementSubsteps.resolve(movement, (step, offset) ->
            new Vector3d(Math.min(step.x(), Math.max(0.0, 0.7 - offset.x())), step.y(), step.z()));
        assertEquals(0.7, result.x, 1e-9);
        assertEquals(movement.y, result.y);
        assertEquals(movement.z, result.z);
    }

    @Test
    void slidingUsesTheResolvedPosition() {
        Vector3d result = MovementSubsteps.resolve(new Vector3d(5.0, 0.0, 5.0), (step, offset) ->
            new Vector3d(Math.min(step.x(), Math.max(0.0, 0.7 - offset.x())), step.y(),
                Math.min(step.z(), Math.max(0.0, 2.3 - offset.z()))));
        assertEquals(0.7, result.x, 1e-9);
        assertEquals(2.3, result.z, 1e-9);
    }

    @Test
    void extremeMovementRemainsBounded() {
        int[] calls = {0};
        Vector3d movement = new Vector3d(30_000_000.0, -0.78, 0.13);
        assertEquals(movement, MovementSubsteps.resolve(movement, (step, offset) -> {
            assertTrue(++calls[0] <= 1024);
            return step;
        }));
    }
}
