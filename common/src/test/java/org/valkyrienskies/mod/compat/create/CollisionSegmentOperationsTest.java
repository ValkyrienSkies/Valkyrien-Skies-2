package org.valkyrienskies.mod.compat.create;

import java.lang.reflect.Proxy;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.valkyrienskies.core.api.bodies.ServerVsBody;
import org.valkyrienskies.core.api.bodies.shape.BodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.BoxBodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.CompoundBodyShapeData;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionSegmentOperationsTest {
    private static final int SEGMENT_ID = 3;

    @Test
    void successfulUpdateDoesNotInspectOrConvertTheShape() {
        ServerVsBody body = (ServerVsBody) Proxy.newProxyInstance(ServerVsBody.class.getClassLoader(),
            new Class<?>[] {ServerVsBody.class}, (proxy, method, args) -> {
                if (method.getName().equals("setCollisionSegmentTransform")) {
                    return null;
                }
                throw new AssertionError("Unexpected body access: " + method.getName());
            });
        assertTrue(update(body));
    }

    @Test
    void missingSegmentRequestsBindingCleanup() {
        assertFalse(update(bodyWithFailure(compound(0, 2, 4), new IllegalArgumentException("missing child"))));
    }

    @Test
    void missingShapeRequestsBindingCleanup() {
        assertFalse(update(bodyWithFailure(null, new IllegalStateException("missing shape"))));
    }

    @Test
    void nonCompoundShapeRequestsBindingCleanup() {
        assertFalse(update(bodyWithFailure(new BoxBodyShapeData(new Vector3d(1)),
            new IllegalArgumentException("not compound"))));
    }

    @Test
    void existingSegmentPreservesArgumentAndStateFailures() {
        for (RuntimeException failure : List.of(new IllegalArgumentException("invalid transform"),
            new IllegalStateException("invalid body state"))) {
            assertSame(failure, assertThrows(failure.getClass(),
                () -> update(bodyWithFailure(compound(0, SEGMENT_ID, 4), failure))));
        }
    }

    @Test
    void unrelatedFailuresAreNotHidden() {
        RuntimeException failure = new UnsupportedOperationException("not supported");
        assertSame(failure, assertThrows(UnsupportedOperationException.class,
            () -> update(bodyWithFailure(null, failure))));
    }

    @Test
    void removingMissingShapeDoesNotPreventBindingCleanup() {
        assertFalse(CollisionSegmentOperations.remove(bodyWithFailure(null,
            new IllegalStateException("missing shape")), SEGMENT_ID));
    }

    @Test
    void removingNonCompoundShapeDoesNotPreventBindingCleanup() {
        assertFalse(CollisionSegmentOperations.remove(bodyWithFailure(new BoxBodyShapeData(new Vector3d(1)),
            new IllegalArgumentException("not compound")), SEGMENT_ID));
    }

    @Test
    void removalOfAnExistingSegmentPreservesFailures() {
        for (RuntimeException failure : List.of(new IllegalArgumentException("invalid removal"),
            new IllegalStateException("invalid body state"))) {
            assertSame(failure, assertThrows(failure.getClass(),
                () -> CollisionSegmentOperations.remove(bodyWithFailure(compound(0, SEGMENT_ID), failure), SEGMENT_ID)));
        }
    }

    private static boolean update(ServerVsBody body) {
        return CollisionSegmentOperations.update(body, SEGMENT_ID, new Vector3d(), new Quaterniond(),
            new Vector3d(), new Vector3d());
    }

    private static ServerVsBody bodyWithFailure(BodyShapeData shape, RuntimeException failure) {
        return (ServerVsBody) Proxy.newProxyInstance(ServerVsBody.class.getClassLoader(),
            new Class<?>[] {ServerVsBody.class}, (proxy, method, args) -> {
                if (method.getName().equals("setCollisionSegmentTransform") || method.getName().equals("removeCollisionSegment")) {
                    throw failure;
                }
                if (method.getName().equals("getCollisionShape")) {
                    return shape;
                }
                throw new AssertionError("Unexpected body access: " + method.getName());
            });
    }

    private static CompoundBodyShapeData compound(int... ids) {
        return new CompoundBodyShapeData(java.util.Arrays.stream(ids).mapToObj(id ->
            new CompoundBodyShapeData.Child(new BoxBodyShapeData(new Vector3d(1)),
                new Vector3d(), new Quaterniond(), 1, new Vector3d(), id)).toList());
    }
}
