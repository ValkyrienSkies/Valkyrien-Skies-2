package org.valkyrienskies.mod.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.junit.jupiter.api.Test;

class ShipSneakingDragTest {
    private static AABBd player(final double x, final double y, final double z) {
        return new AABBd(x - 0.3, y, z - 0.3, x + 0.3, y + 1.5, z + 0.3);
    }

    private static Matrix4d transform(final double yaw, final double roll, final double x, final double z) {
        return new Matrix4d().translation(x + 0.5, 0.5, z + 0.5).rotateY(yaw).rotateZ(roll)
            .translate(-0.5, -0.5, -0.5);
    }

    private static Vector3d centerDrag(final AABBd box, final Matrix4d previous, final Matrix4d current) {
        final Vector3d position = new Vector3d((box.minX + box.maxX) * 0.5, box.minY, (box.minZ + box.maxZ) * 0.5);
        return current.transformPosition(previous.invert(new Matrix4d()).transformPosition(position, new Vector3d()))
            .sub(position);
    }

    private static boolean supported(final AABBd box, final Matrix4d transform) {
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(transform).addBox(0, 0, 0, 1, 1, 1);
        return query.hasSupport(box.minX, box.minY - 0.6, box.minZ, box.maxX, box.maxY - 0.6, box.maxZ);
    }

    private static ShipSneakingDrag support(final Matrix4d previous, final Matrix4d current) {
        final ShipSneakingDrag support = new ShipSneakingDrag(previous, current);
        support.addBox(0, 0, 0, 1, 1, 1);
        return support;
    }

    @Test
    void rotationCanRemoveCornerSupportEvenWithoutWalking() {
        final Matrix4d previous = new Matrix4d();
        final Matrix4d current = transform(0.1, 0, 10, -5);
        final AABBd box = player(1.29, 1, 1.29);
        final Vector3d drag = centerDrag(box, previous, current);
        assertTrue(supported(box, previous));
        assertFalse(supported(new AABBd(box).translate(drag), current));
        final Vector3dc corrected = support(previous, current).adjustDrag(box, drag, 0.6);
        assertTrue(supported(new AABBd(box).translate(corrected), current));
        assertTrue(corrected.distance(drag) < 0.1);
    }

    @Test
    void repeatedTurningAndTranslationKeepAnOverhangingPlayerSupported() {
        for (final double roll : new double[] {0, Math.PI, Math.PI / 2}) {
            Matrix4d previous = transform(0, roll, 0, 0);
            final AABBd box = player(1.29, 1, 1.29);
            for (int tick = 1; tick <= 1000; tick++) {
                final Matrix4d current = transform(tick * 0.07, roll, tick * 0.1, -tick * 0.2);
                final Vector3dc drag = support(previous, current).adjustDrag(box, centerDrag(box, previous, current), 0.6);
                box.translate(drag);
                assertTrue(supported(box, current), "Lost support at tick " + tick + ", roll " + roll);
                assertEquals(1.0, box.minY, 2.0E-7);
                previous = current;
            }
        }
    }

    @Test
    void translationRetainsItsFullMovementIncludingVerticalCarry() {
        final Matrix4d previous = new Matrix4d();
        final Matrix4d current = new Matrix4d().translation(15, 3, -20);
        final AABBd box = player(1.29, 1, 1.29);
        final Vector3d drag = centerDrag(box, previous, current);
        assertSame(drag, support(previous, current).adjustDrag(box, drag, 0.6));
    }

    @Test
    void supportedInteriorRetainsTheOriginalDrag() {
        final Matrix4d previous = new Matrix4d();
        final Matrix4d current = transform(0.5, 0, 2, -3);
        final AABBd box = player(0.5, 1, 0.5);
        final Vector3d drag = centerDrag(box, previous, current);
        assertSame(drag, support(previous, current).adjustDrag(box, drag, 0.6));
    }

    @Test
    void airborneOrAlreadyUnsupportedPlayersAreNotPulledBackToTheDeck() {
        final Matrix4d previous = new Matrix4d();
        final Matrix4d current = transform(0.5, 0, 0, 0);
        for (final AABBd box : new AABBd[] {player(2, 1, 2), player(1.29, 1.8, 1.29), player(1.29, 0.8, 1.29)}) {
            final Vector3d drag = centerDrag(box, previous, current);
            assertSame(drag, support(previous, current).adjustDrag(box, drag, 0.6));
        }
    }

    @Test
    void emptyShapesLeaveDraggingAlone() {
        final Vector3d drag = new Vector3d(1, 2, 3);
        assertSame(drag, new ShipSneakingDrag(new Matrix4d(), new Matrix4d().rotateY(0.1))
            .adjustDrag(player(1.29, 1, 1.29), drag, 0.6));
    }

    @Test
    void rockingAtASteepCornerRecoversAStandingContact() {
        final Matrix4d previous = transform(0.4, 0.6 + Math.sin(16 * 0.08) * 0.15, 16 * 0.12, -16 * 0.09)
            .translateLocal(0, Math.sin(16 * 0.04) * 0.2, 0);
        final Matrix4d current = transform(0.4, 0.6 + Math.sin(17 * 0.08) * 0.15, 17 * 0.12, -17 * 0.09)
            .translateLocal(0, Math.sin(17 * 0.04) * 0.2, 0);
        // Reached by walking downhill before the ship rolls further at tick end.
        final AABBd box = new AABBd(0.9804350436189977, 0.6547731607884293, -1.2105884081802984,
            1.580435043618999, 2.154773160788429, -0.6105884081802985);
        final Vector3d drag = new Vector3d(0.1198662965018802, 0.0032013197760867, -0.0899434710676735);
        assertTrue(supported(box, previous));
        assertFalse(supported(new AABBd(box).translate(drag), current));
        final Vector3dc corrected = support(previous, current).adjustDrag(box, drag, 0.6);
        final AABBd carried = new AABBd(box).translate(corrected);
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(current).addBox(0, 0, 0, 1, 1, 1);
        assertTrue(supported(carried, current));
        assertTrue(query.hasStandingSupport(carried.minX, carried.minY, carried.minZ,
            carried.maxX, carried.maxY, carried.maxZ));
        assertTrue(corrected.distance(drag) < 0.6);
    }

    @Test
    void pitchingSettlesTheFeetOntoTheNewSurfaceHeight() {
        final Matrix4d previous = new Matrix4d();
        final Matrix4d current = transform(0.4, 0.15, 0, 0);
        final AABBd box = player(0.5, 1, 0.5);
        final Vector3dc corrected = support(previous, current).adjustDrag(box, centerDrag(box, previous, current), 0.6);
        final AABBd carried = new AABBd(box).translate(corrected);
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(current).addBox(0, 0, 0, 1, 1, 1);
        assertTrue(query.hasStandingSupport(carried.minX, carried.minY, carried.minZ,
            carried.maxX, carried.maxY, carried.maxZ));
    }
}
