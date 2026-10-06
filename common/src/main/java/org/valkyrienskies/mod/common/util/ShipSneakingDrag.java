package org.valkyrienskies.mod.common.util;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;

/** Keeps a sneaking player's footprint supported when their ship rotates underneath it. */
public final class ShipSneakingDrag {
    private static final double CONTACT_EPSILON = 1.0E-7;
    private final Matrix4dc previousToWorld;
    private final Matrix4dc previousToCurrent;
    private final ShipCollisionQuery previous = new ShipCollisionQuery();
    private final ShipCollisionQuery.ShipShapes previousShapes;
    private final ShipCollisionQuery current = new ShipCollisionQuery();
    private final ShipCollisionQuery.ShipShapes currentShapes;
    private final List<double[]> boxes = new ArrayList<>();

    public ShipSneakingDrag(final Matrix4dc previousToWorld, final Matrix4dc currentToWorld) {
        this.previousToWorld = new Matrix4d(previousToWorld);
        previousToCurrent = new Matrix4d(currentToWorld).mul(previousToWorld.invert(new Matrix4d()));
        previousShapes = previous.addShip(previousToWorld);
        currentShapes = current.addShip(currentToWorld);
    }

    public void addBox(final double minX, final double minY, final double minZ,
        final double maxX, final double maxY, final double maxZ) {
        currentShapes.addBox(minX, minY, minZ, maxX, maxY, maxZ);
        previousShapes.addBox(minX, minY, minZ, maxX, maxY, maxZ);
        boxes.add(new double[] {minX, minY, minZ, maxX, maxY, maxZ});
    }

    public Vector3dc adjustDrag(final AABBdc player, final Vector3dc drag, final double stepHeight) {
        // Like vanilla's isAboveGround, permit a short step down while falling,
        // but require support on this ship before applying any correction.
        final double previousHeight = previous.supportHeight(player.minX(), player.minY() - stepHeight,
            player.minZ(), player.maxX(), player.maxY() - stepHeight, player.maxZ());
        if (Double.isNaN(previousHeight) || previousHeight > player.minY() + 0.05) {
            return drag;
        }
        final double carriedHeight = supportHeight(player, drag, stepHeight);
        if (!Double.isNaN(carriedHeight) && Math.abs(carriedHeight - player.minY() - drag.y()) <= stepHeight) {
            // Pitch/roll can lift or lower the supporting corner even when the
            // center's carried position still overlaps the deck horizontally.
            return Math.abs(carriedHeight - player.minY() - drag.y()) < CONTACT_EPSILON ? drag
                : new Vector3d(drag.x(), carriedHeight - player.minY() + CONTACT_EPSILON, drag.z());
        }

        // A player keeps a world-aligned box while the ship rotates. Carrying its
        // center can therefore rotate an overhanging corner completely off its
        // support. Only in that case, carry an actual point under the old feet.
        final double inset = ShipCollisionQuery.standingInset(player.maxY() - player.minY()) + 1.0E-4;
        final double[] clipMin = {player.minX(), player.minY() - stepHeight, player.minZ()};
        final double[] clipMax = {player.maxX(), player.minY() + 0.05, player.maxZ()};
        Vector3dc best = drag;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (final double[] box : boxes) {
            // An edge between faces can also provide an upward SAT response,
            // so retain all faces and validate the resulting standing contact.
            for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
                final int axis = faceIndex / 2;
                final int u = (axis + 1) % 3;
                final int v = (axis + 2) % 3;
                List<Vector3d> face = new ArrayList<>(4);
                for (int corner = 0; corner < 4; corner++) {
                    final Vector3d point = new Vector3d();
                    point.setComponent(axis, box[axis + (faceIndex % 2 == 0 ? 0 : 3)]);
                    point.setComponent(u, box[u + (corner == 1 || corner == 2 ? 3 : 0)]);
                    point.setComponent(v, box[v + (corner >= 2 ? 3 : 0)]);
                    face.add(previousToWorld.transformPosition(point));
                }
                final Vector3d faceCenter = new Vector3d();
                for (final Vector3d point : face) {
                    faceCenter.add(point);
                }
                faceCenter.mul(0.25);
                for (int clipAxis = 0; clipAxis < 3 && !face.isEmpty(); clipAxis++) {
                    face = clip(face, clipAxis, clipMin[clipAxis], true);
                    face = clip(face, clipAxis, clipMax[clipAxis], false);
                }
                if (face.size() < 3) {
                    continue;
                }
                final Vector3d anchor = new Vector3d();
                for (final Vector3d point : face) {
                    anchor.add(point);
                }
                anchor.div(face.size());
                final Vector3d candidate = previousToCurrent.transformPosition(anchor, new Vector3d()).sub(anchor);
                // Keep the carried contact inside the inset used by the next
                // sneak probe, including a little room for floating-point error.
                candidate.x += anchor.x - Math.max(player.minX() + inset, Math.min(player.maxX() - inset, anchor.x));
                candidate.z += anchor.z - Math.max(player.minZ() + inset, Math.min(player.maxZ() - inset, anchor.z));
                double height = supportHeight(player, candidate, stepHeight);
                if (Double.isNaN(height)) {
                    // A newly tilted side can obscure the contact. Move only as
                    // far into the supporting face as needed to recover support.
                    final Vector3d centered = previousToCurrent.transformPosition(faceCenter, new Vector3d())
                        .sub((player.minX() + player.maxX()) * 0.5, 0.0,
                            (player.minZ() + player.maxZ()) * 0.5);
                    centered.y = candidate.y;
                    final double centeredHeight = supportHeight(player, centered, stepHeight);
                    if (!Double.isNaN(centeredHeight)) {
                        double low = 0.0;
                        double high = 1.0;
                        final Vector3d trial = new Vector3d();
                        for (int iteration = 0; iteration < 8; iteration++) {
                            final double fraction = (low + high) * 0.5;
                            candidate.lerp(centered, fraction, trial);
                            if (Double.isNaN(supportHeight(player, trial, stepHeight))) {
                                low = fraction;
                            } else {
                                high = fraction;
                            }
                        }
                        candidate.lerp(centered, high);
                        height = supportHeight(player, candidate, stepHeight);
                    }
                }
                if (Double.isNaN(height) || Math.abs(height - player.minY() - candidate.y) > stepHeight) {
                    continue;
                }
                // Rotation also changes which corner of the feet touches a slope.
                // Settle vertically onto the highest supporting surface in the box.
                candidate.y = height - player.minY() + CONTACT_EPSILON;
                final double distance = candidate.distanceSquared(drag);
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private double supportHeight(final AABBdc player, final Vector3dc drag, final double stepHeight) {
        if (!current.hasSupport(player.minX() + drag.x(), player.minY() + drag.y() - stepHeight,
            player.minZ() + drag.z(), player.maxX() + drag.x(), player.maxY() + drag.y() - stepHeight,
            player.maxZ() + drag.z())) {
            return Double.NaN;
        }
        final double height = current.supportHeight(player.minX() + drag.x(), player.minY() + drag.y() - stepHeight,
            player.minZ() + drag.z(), player.maxX() + drag.x(), player.maxY() + drag.y() - stepHeight,
            player.maxZ() + drag.z());
        return !Double.isNaN(height) && current.hasStandingSupport(player.minX() + drag.x(), height + CONTACT_EPSILON,
            player.minZ() + drag.z(), player.maxX() + drag.x(), height + CONTACT_EPSILON + player.maxY() - player.minY(),
            player.maxZ() + drag.z()) ? height : Double.NaN;
    }

    private static List<Vector3d> clip(final List<Vector3d> points, final int axis, final double boundary,
        final boolean keepAbove) {
        if (points.isEmpty()) {
            return points;
        }
        final List<Vector3d> result = new ArrayList<>(points.size() + 1);
        Vector3d previous = points.get(points.size() - 1);
        double previousDistance = (previous.get(axis) - boundary) * (keepAbove ? 1.0 : -1.0);
        for (final Vector3d point : points) {
            final double distance = (point.get(axis) - boundary) * (keepAbove ? 1.0 : -1.0);
            if ((distance >= 0.0) != (previousDistance >= 0.0)) {
                result.add(previous.lerp(point, previousDistance / (previousDistance - distance), new Vector3d()));
            }
            if (distance >= 0.0) {
                result.add(point);
            }
            previous = point;
            previousDistance = distance;
        }
        return result;
    }
}
