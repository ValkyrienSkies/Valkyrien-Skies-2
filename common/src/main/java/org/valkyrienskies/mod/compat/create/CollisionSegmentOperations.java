package org.valkyrienskies.mod.compat.create;

import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.bodies.ServerVsBody;
import org.valkyrienskies.core.api.bodies.shape.CompoundBodyShapeData;

final class CollisionSegmentOperations {
    private CollisionSegmentOperations() {
    }

    static boolean update(ServerVsBody body, int segmentId, Vector3dc position, Quaterniondc rotation,
        Vector3dc velocity, Vector3dc angularVelocity) {
        try {
            body.setCollisionSegmentTransform(segmentId, position, rotation, velocity, angularVelocity);
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return missingSegmentOrThrow(body, segmentId, exception);
        }
    }

    static boolean remove(ServerVsBody body, int segmentId) {
        // No child can remain when the compound shape itself has been removed.
        // Do not catch removal failures: core may already have mutated the shape.
        if (!(body.getCollisionShape() instanceof CompoundBodyShapeData)) {
            return false;
        }
        return body.removeCollisionSegment(segmentId);
    }

    private static boolean missingSegmentOrThrow(ServerVsBody body, int segmentId, RuntimeException exception) {
        // Teardown can remove the core child before the contraption stops ticking.
        // Only recover a missing binding; preserve failures for children that still exist.
        // Inspect the shape on failure only, avoiding a shape conversion on every tick.
        if (body.getCollisionShape() instanceof CompoundBodyShapeData compound) {
            for (CompoundBodyShapeData.Child child : compound.getChildren()) {
                if (child.getSegmentId() == segmentId) {
                    throw exception;
                }
            }
        }
        // The caller clears the stale binding and previous pose before retrying next tick.
        return false;
    }
}
