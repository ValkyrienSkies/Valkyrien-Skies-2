package org.valkyrienskies.mod.common.util;

import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ShipDraggingMath {
    public static boolean landedOnWorld(Vector3dc requested, Vector3dc resolved) {
        return requested.y() < 0.0 && resolved.y() > requested.y() + 1e-7;
    }

    private ShipDraggingMath() {
    }

    public static Vector3d movement(final Vector3dc position, final Matrix4dc previousWorldToShip,
        final Matrix4dc currentShipToWorld) {
        return currentShipToWorld.transformPosition(previousWorldToShip.transformPosition(position, new Vector3d()))
            .sub(position);
    }

    public static Vector3d renderPosition(final Vector3dc previousPosition, final Vector3dc currentPosition,
        final Matrix4dc previousWorldToShip, final Matrix4dc currentWorldToShip,
        final Matrix4dc renderShipToWorld, final double partialTick) {
        final Vector3d previous = previousWorldToShip.transformPosition(previousPosition, new Vector3d());
        final Vector3d current = currentWorldToShip.transformPosition(currentPosition, new Vector3d());
        return renderShipToWorld.transformPosition(previous.lerp(current, partialTick));
    }

    public static double shipYaw(final Quaterniondc rotation) {
        final Vector3d forward = rotation.transform(new Vector3d(0.0, 0.0, 1.0));
        if (forward.x * forward.x + forward.z * forward.z > 1.0E-12) {
            return Math.atan2(forward.x, forward.z);
        }
        final Vector3d right = rotation.transform(new Vector3d(1.0, 0.0, 0.0));
        return Math.atan2(-right.z, right.x);
    }

    public static double draggedYaw(final Quaterniondc previousRotation, final Quaterniondc currentRotation,
        final double worldYawDegrees) {
        final double yaw = Math.toRadians(worldYawDegrees);
        final Vector3d forward = new Vector3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
        previousRotation.transformInverse(forward);
        currentRotation.transform(forward);
        if (!forward.isFinite() || forward.x * forward.x + forward.z * forward.z < 1.0E-12) {
            return 0.0;
        }
        return wrapDegrees(Math.toDegrees(-Math.atan2(forward.x, forward.z)) - worldYawDegrees);
    }

    public static double yawToShip(final Quaterniondc rotation, final double worldYawDegrees) {
        return Math.toRadians(worldYawDegrees) + shipYaw(rotation);
    }

    public static double yawToWorld(final Quaterniondc rotation, final double shipYawRadians) {
        return wrapDegrees(Math.toDegrees(shipYawRadians - shipYaw(rotation)));
    }

    public static double wrapDegrees(final double angle) {
        final double wrapped = angle % 360.0;
        return wrapped >= 180.0 ? wrapped - 360.0 : wrapped < -180.0 ? wrapped + 360.0 : wrapped;
    }
}
