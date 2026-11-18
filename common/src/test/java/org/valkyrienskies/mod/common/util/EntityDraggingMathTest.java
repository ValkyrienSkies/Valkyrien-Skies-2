package org.valkyrienskies.mod.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class EntityDraggingMathTest {
    @Test
    void rockingAcrossVerticalDoesNotFlipPlayerYaw() {
        final Quaterniond previous = new Quaterniond().rotateX(Math.toRadians(89));
        final Quaterniond current = new Quaterniond().rotateX(Math.toRadians(91));
        assertEquals(0, ShipDraggingMath.draggedYaw(previous, current, 0), 1e-10);
        for (double yaw : new double[] {-179, -90, -45, 0, 45, 90, 179}) {
            assertTrue(Math.abs(ShipDraggingMath.draggedYaw(previous, current, yaw)) < 0.1);
        }
    }

    @Test
    void dragYawDoesNotDependOnShipAssemblyOrientation() {
        final Quaterniond previous = new Quaterniond().rotateX(Math.toRadians(90));
        final Quaterniond delta = new Quaterniond().rotateY(Math.toRadians(3)).rotateZ(Math.toRadians(2));
        final Quaterniond current = new Quaterniond(delta).mul(previous);
        for (double yaw : new double[] {-179, -90, 0, 45, 179}) {
            assertEquals(ShipDraggingMath.draggedYaw(new Quaterniond(), delta, yaw),
                ShipDraggingMath.draggedYaw(previous, current, yaw), 1e-10);
        }
    }

    @Test
    void dragYawPreservesTurningDirectionAcrossAngleWrap() {
        for (double yaw : new double[] {-179, 0, 179, 720}) {
            assertEquals(-10, ShipDraggingMath.draggedYaw(new Quaterniond(),
                new Quaterniond().rotateY(Math.toRadians(10)), yaw), 1e-10);
        }
    }

    @Test
    void verticalDraggedLookKeepsPreviousYaw() {
        assertEquals(0, ShipDraggingMath.draggedYaw(new Quaterniond(),
            new Quaterniond().rotateX(Math.toRadians(90)), 0), 1e-10);
    }

    @Test
    void wallsAndCeilingsDoNotCountAsLandingOnWorldTerrain() {
        assertFalse(ShipDraggingMath.landedOnWorld(new Vector3d(1, -0.08, 0), new Vector3d(0, -0.08, 0)));
        assertFalse(ShipDraggingMath.landedOnWorld(new Vector3d(0, 1, 0), new Vector3d()));
        assertTrue(ShipDraggingMath.landedOnWorld(new Vector3d(1, -0.08, 0), new Vector3d(1, 0, 0)));
    }

    @Test
    void changingScaleCannotMakeDragOrYawNonfinite() {
        final Quaterniond rotation = new Quaterniond().rotateY(0.7).rotateZ(1.0);
        final Matrix4d current = new Matrix4d().rotate(rotation).scale(8);
        assertTrue(ShipDraggingMath.movement(new Vector3d(10, 2, 3), new Matrix4d(), current).isFinite());
        assertTrue(Double.isFinite(ShipDraggingMath.yawToWorld(rotation, Math.toRadians(45))));
    }

    @Test
    void floorContactPreservesAllHorizontalSubsteps() {
        for (double speed : new double[] {0.49, 0.5, 1.0, 5.0}) {
            final Vector3d result = MovementSubsteps.resolve(new Vector3d(speed, -0.08, 0),
                (step, offset) -> new Vector3d(step.x(), 0, step.z()));
            assertEquals(speed, result.x, 1e-10);
            assertEquals(0, result.y, 1e-10);
        }
    }

    @Test
    void subsequentCollisionsUseTheClippedPosition() {
        final Vector3d result = MovementSubsteps.resolve(new Vector3d(2, 0, 2),
            (step, offset) -> new Vector3d(Math.min(step.x(), 0.3 - offset.x()), 0, step.z()));
        assertEquals(0.3, result.x, 1e-10);
        assertEquals(2, result.z, 1e-10);
    }

    @Test
    void rotationCarriesThePositionAfterWalking() {
        final Matrix4d current = new Matrix4d().rotateY(Math.toRadians(10));
        final Vector3d position = new Vector3d(10.3, 0, 0);
        final Vector3d moved = new Vector3d(position).add(
            ShipDraggingMath.movement(position, new Matrix4d(), current));
        assertTrue(moved.equals(current.transformPosition(position, new Vector3d()), 1e-10));
    }

    @Test
    void renderingEndsAtTheSimulatedPositionIncludingSupportCorrections() {
        final Matrix4d current = new Matrix4d().rotateY(Math.toRadians(10));
        final Vector3d previous = new Vector3d(10, 0, 0);
        final Vector3d position = current.transformPosition(new Vector3d(10.3, 0, 0)).add(0, 0, 0.1);
        final Vector3d rendered = ShipDraggingMath.renderPosition(previous, position,
            new Matrix4d(), current.invert(new Matrix4d()), current, 1);
        assertTrue(rendered.equals(position, 1e-10));
        assertTrue(ShipDraggingMath.renderPosition(previous, position,
            new Matrix4d(), current.invert(new Matrix4d()), new Matrix4d(), 0).equals(previous, 1e-10));
    }

    @Test
    void stationaryPassengerStaysAtTheSameLocalPositionThroughoutRendering() {
        final Vector3d local = new Vector3d(5, 2, -3);
        final Matrix4d previous = new Matrix4d().translation(30, 4, -20);
        final Matrix4d current = new Matrix4d().translation(31, 5, -20).rotateY(0.4);
        final Matrix4d render = new Matrix4d().translation(30.5, 4.5, -20).rotateY(0.2);
        final Vector3d result = ShipDraggingMath.renderPosition(
            previous.transformPosition(local, new Vector3d()), current.transformPosition(local, new Vector3d()),
            previous.invert(new Matrix4d()), current.invert(new Matrix4d()), render, 0.5);
        assertTrue(result.equals(render.transformPosition(local, new Vector3d()), 1e-10));
    }

    @Test
    void yawRoundTripsOnTiltedAndVerticalShips() {
        for (double roll : new double[] {0, 30, 60, 90, 180}) {
            for (double pitch : new double[] {0, 45, 90}) {
                final Quaterniond rotation = new Quaterniond().rotateY(0.7)
                    .rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll));
                for (double yaw : new double[] {-179, -45, 0, 45, 179}) {
                    final double result = ShipDraggingMath.yawToWorld(rotation,
                        ShipDraggingMath.yawToShip(rotation, yaw));
                    assertEquals(0, ShipDraggingMath.wrapDegrees(result - yaw), 1e-10);
                }
            }
        }
    }

    @Test
    void yawUsesMinecraftRotationDirection() {
        final Quaterniond rotation = new Quaterniond().rotateY(Math.toRadians(30));
        assertEquals(-30, ShipDraggingMath.yawToWorld(rotation, 0), 1e-10);
        assertEquals(0, ShipDraggingMath.yawToShip(rotation, -30), 1e-10);
    }
}
