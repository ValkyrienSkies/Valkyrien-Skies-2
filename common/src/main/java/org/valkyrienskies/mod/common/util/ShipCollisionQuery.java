package org.valkyrienskies.mod.common.util;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

/**
 * A snapshot of nearby ship collision boxes, reused for one movement's support checks.
 * Transforms and separating axes are prepared once; support checks allocate nothing
 * and do not read chunks, change dragging state, or use render interpolation.
 */
public final class ShipCollisionQuery {
    private static final double EPSILON = 1.0E-7;
    private static final double MIN_WALKABLE_NORMAL_Y = Math.sin(Math.toRadians(45.0));
    // VS-core's EntityPolygonCollider.canStep4 insets the feet by height * 1e-3 and
    // requires a collision response longer than 1e-4. Smaller overlaps can be pushed
    // sideways by gravity even after vanilla has reduced horizontal movement to zero.
    private static final double STANDING_INSET_PER_HEIGHT = 1.0E-3;
    private static final double MIN_STANDING_RESPONSE = 1.0E-4;

    private final List<ShipShapes> ships = new ArrayList<>();

    public ShipShapes addShip(final Matrix4dc shipToWorld) {
        final ShipShapes shapes = new ShipShapes(shipToWorld);
        ships.add(shapes);
        return shapes;
    }

    public boolean hasSupport(final double minX, final double minY, final double minZ,
        final double maxX, final double maxY, final double maxZ) {
        return !Double.isNaN(supportHeight(minX, minY, minZ, maxX, maxY, maxZ, false));
    }

    /** Highest walkable contact under the full footprint, or NaN when none exists in the probe. */
    public double supportHeight(final double minX, final double minY, final double minZ,
        final double maxX, final double maxY, final double maxZ) {
        return supportHeight(minX, minY, minZ, maxX, maxY, maxZ, true);
    }

    /** Whether VS-core's below-feet slice resolves upward instead of out through a nearby edge. */
    public boolean hasStandingSupport(final double minX, final double feetY, final double minZ,
        final double maxX, final double maxY, final double maxZ) {
        final double halfDepth = (maxY - feetY) * STANDING_INSET_PER_HEIGHT * 0.5;
        final double halfX = (maxX - minX) * 0.5;
        final double halfZ = (maxZ - minZ) * 0.5;
        for (int shipIndex = 0; shipIndex < ships.size(); shipIndex++) {
            final ShipShapes ship = ships.get(shipIndex);
            for (int boxIndex = 0; boxIndex < ship.boxes.size(); boxIndex++) {
                final Box box = ship.boxes.get(boxIndex);
                final double dx = (minX + maxX) * 0.5 - box.center.x;
                final double dy = feetY - halfDepth - box.center.y;
                final double dz = (minZ + maxZ) * 0.5 - box.center.z;
                double shortestResponse = Double.POSITIVE_INFINITY;
                boolean upward = false;
                for (int i = 0; i < ship.separatingAxes.size(); i++) {
                    final Vector3d normal = ship.separatingAxes.get(i);
                    final double radius = box.radii[i] + Math.abs(normal.x) * halfX
                        + Math.abs(normal.y) * halfDepth + Math.abs(normal.z) * halfZ;
                    final double offset = normal.x * dx + normal.y * dy + normal.z * dz;
                    final double overlap = radius - Math.abs(offset);
                    if (overlap <= 0.0) {
                        upward = false;
                        break;
                    }
                    final boolean walkable = Math.abs(normal.y) >= MIN_WALKABLE_NORMAL_Y;
                    final double response = overlap / (walkable ? Math.abs(normal.y)
                        : Math.sqrt(normal.x * normal.x + normal.z * normal.z));
                    if (response < shortestResponse) {
                        shortestResponse = response;
                        upward = walkable && offset * normal.y > 0.0 && response > MIN_STANDING_RESPONSE;
                    }
                }
                if (upward) {
                    return true;
                }
            }
        }
        return false;
    }

    private double supportHeight(final double minX, final double minY, final double minZ,
        final double maxX, final double maxY, final double maxZ, final boolean highest) {
        // Boolean probes need a stable overlap margin; settling needs the actual
        // contact height at the outer edge of the player's collision box.
        final double inset = highest ? 0.0 : standingInset(maxY - minY);
        final double halfX = (maxX - minX) * 0.5 - inset;
        final double halfZ = (maxZ - minZ) * 0.5 - inset;
        if (halfX <= 0.0 || halfZ <= 0.0 || maxY - minY <= EPSILON * 2.0) {
            return Double.NaN;
        }
        final double centerX = (minX + maxX) * 0.5;
        final double centerZ = (minZ + maxZ) * 0.5;
        double result = Double.NaN;
        for (int i = 0; i < ships.size(); i++) {
            final ShipShapes ship = ships.get(i);
            for (int j = 0; j < ship.boxes.size(); j++) {
                final Box box = ship.boxes.get(j);
                final double dx = centerX - box.center.x;
                final double dz = centerZ - box.center.z;
                if (Math.abs(dx) >= halfX + box.worldHalfExtents.x
                    || Math.abs(dz) >= halfZ + box.worldHalfExtents.z
                    || minY + EPSILON >= box.center.y + box.worldHalfExtents.y
                    || maxY - EPSILON <= box.center.y - box.worldHalfExtents.y) {
                    continue;
                }
                final double height = ship.supportHeight(box, dx, dz, halfX, halfZ, minY, maxY);
                if (!Double.isNaN(height)) {
                    if (!highest) {
                        return height;
                    }
                    result = Double.isNaN(result) ? height : Math.max(result, height);
                }
            }
        }
        return result;
    }

    static double standingInset(final double height) {
        return height * STANDING_INSET_PER_HEIGHT + MIN_STANDING_RESPONSE + EPSILON;
    }

    public static final class ShipShapes {
        private final Matrix4dc transform;
        private final Vector3d axisX;
        private final Vector3d axisY;
        private final Vector3d axisZ;
        private final double scaleX;
        private final double scaleY;
        private final double scaleZ;
        private final List<Vector3d> separatingAxes = new ArrayList<>(15);
        private final List<Box> boxes = new ArrayList<>();

        private ShipShapes(final Matrix4dc shipToWorld) {
            transform = new Matrix4d(shipToWorld);
            axisX = transform.transformDirection(new Vector3d(1.0, 0.0, 0.0));
            axisY = transform.transformDirection(new Vector3d(0.0, 1.0, 0.0));
            axisZ = transform.transformDirection(new Vector3d(0.0, 0.0, 1.0));
            scaleX = axisX.length();
            scaleY = axisY.length();
            scaleZ = axisZ.length();
            axisX.div(scaleX);
            axisY.div(scaleY);
            axisZ.div(scaleZ);
            separatingAxes.add(new Vector3d(1.0, 0.0, 0.0));
            separatingAxes.add(new Vector3d(0.0, 1.0, 0.0));
            separatingAxes.add(new Vector3d(0.0, 0.0, 1.0));
            addAxes(axisX);
            addAxes(axisY);
            addAxes(axisZ);
        }

        private void addAxes(final Vector3d shipAxis) {
            separatingAxes.add(shipAxis);
            for (int i = 0; i < 3; i++) {
                final Vector3d cross = shipAxis.cross(separatingAxes.get(i), new Vector3d());
                if (cross.lengthSquared() > 1.0E-12) {
                    separatingAxes.add(cross.normalize());
                }
            }
        }

        public void addBox(final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ) {
            final Vector3d center = transform.transformPosition(
                new Vector3d((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5));
            final double halfX = (maxX - minX) * 0.5 * scaleX;
            final double halfY = (maxY - minY) * 0.5 * scaleY;
            final double halfZ = (maxZ - minZ) * 0.5 * scaleZ;
            final Vector3d worldHalfExtents = new Vector3d(
                Math.abs(axisX.x) * halfX + Math.abs(axisY.x) * halfY + Math.abs(axisZ.x) * halfZ,
                Math.abs(axisX.y) * halfX + Math.abs(axisY.y) * halfY + Math.abs(axisZ.y) * halfZ,
                Math.abs(axisX.z) * halfX + Math.abs(axisY.z) * halfY + Math.abs(axisZ.z) * halfZ);
            final double[] radii = new double[separatingAxes.size()];
            for (int i = 0; i < radii.length; i++) {
                final Vector3d normal = separatingAxes.get(i);
                radii[i] = Math.abs(normal.dot(axisX)) * halfX + Math.abs(normal.dot(axisY)) * halfY
                    + Math.abs(normal.dot(axisZ)) * halfZ;
            }
            boxes.add(new Box(center, worldHalfExtents, radii));
        }

        private double supportHeight(final Box box, final double dx, final double dz,
            final double halfX, final double halfZ, final double minY, final double maxY) {
            // SAT for a horizontal footprint swept vertically through the box. The
            // upper end of the intersection interval is the first surface hit from
            // above. Merely intersecting a lower walkable face would also accept a
            // steep side above it, which can slide the player off a rotated corner.
            double bottom = Double.NEGATIVE_INFINITY;
            double top = Double.POSITIVE_INFINITY;
            double topNormalY = 0.0;
            for (int i = 0; i < separatingAxes.size(); i++) {
                final Vector3d normal = separatingAxes.get(i);
                final double radius = box.radii[i] + Math.abs(normal.x) * halfX + Math.abs(normal.z) * halfZ;
                final double horizontalOffset = normal.x * dx + normal.z * dz;
                final double absY = Math.abs(normal.y);
                if (absY < EPSILON) {
                    if (Math.abs(horizontalOffset) >= radius) {
                        return Double.NaN;
                    }
                    continue;
                }
                final double signedOffset = normal.y > 0.0 ? horizontalOffset : -horizontalOffset;
                bottom = Math.max(bottom, (-radius - signedOffset) / absY);
                final double axisTop = (radius - signedOffset) / absY;
                if (axisTop < top) {
                    top = axisTop;
                    topNormalY = absY;
                }
                if (bottom > top) {
                    return Double.NaN;
                }
            }
            final double surfaceY = box.center.y + top;
            return topNormalY >= MIN_WALKABLE_NORMAL_Y && surfaceY > minY + EPSILON && surfaceY < maxY - EPSILON
                ? surfaceY : Double.NaN;
        }
    }

    private record Box(Vector3d center, Vector3d worldHalfExtents, double[] radii) {
    }
}
