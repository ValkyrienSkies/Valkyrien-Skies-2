package org.valkyrienskies.mod.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ShipCollisionQueryTest {
    private static ShipCollisionQuery unitBlock(final Matrix4d transform) {
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(transform).addBox(0, 0, 0, 1, 1, 1);
        return query;
    }

    @Test
    void emptyQueryHasNoSupport() {
        assertFalse(new ShipCollisionQuery().hasSupport(0, 0, 0, 1, 1, 1));
    }

    @Test
    void playerCanOverhangWhileTheirFootprintHasStableSupport() {
        final ShipCollisionQuery query = unitBlock(new Matrix4d());
        // Center is outside the block, but the 0.6-wide player still overlaps by 0.05.
        assertTrue(query.hasSupport(0.95, 0.4, 0.2, 1.55, 1.9, 0.8));
        assertFalse(query.hasSupport(1.0, 0.4, 0.2, 1.6, 1.9, 0.8));
        assertFalse(query.hasSupport(1.05, 0.4, 0.2, 1.65, 1.9, 0.8));
    }

    @Test
    void sliverOfOverlapThatTheCollisionSolverEjectsIsNotSupport() {
        final ShipCollisionQuery query = unitBlock(new Matrix4d());
        // Reproduced by repeatedly pressing into an edge. With this overlap the
        // solver turns (0, -0.0784, 0) into (0.000179447978, -0.0784, 0).
        assertFalse(query.hasSupport(0.999820552022, 0.4, 0.2, 1.599820552022, 1.9, 0.8));
        assertFalse(query.hasSupport(0.999, 0.4, 0.2, 1.599, 1.9, 0.8));
        assertTrue(query.hasSupport(0.998, 0.4, 0.2, 1.598, 1.9, 0.8));
        // The collider's standing inset grows with the entity's height.
        assertFalse(query.hasSupport(0.998, 0.4, 0.2, 1.598, 3.4, 0.8));
        assertTrue(query.hasSupport(0.996, 0.4, 0.2, 1.596, 3.4, 0.8));
    }

    @Test
    void tiltedSideBelowTheDeckDoesNotCountAsSupport() {
        final ShipCollisionQuery query = unitBlock(new Matrix4d()
            .translation(0.5, 0.5, 0.5).rotateZ(0.1).translate(-0.5, -0.5, -0.5));
        // The top edge is at x=0.9476; the side continues out to x=1.0474.
        assertTrue(query.hasSupport(0.94, 0.4, 0.2, 1.54, 1.9, 0.8));
        assertFalse(query.hasSupport(0.96, 0.4, 0.2, 1.56, 1.9, 0.8));
    }

    @Test
    void aLowerWalkableFaceDoesNotHideTheSteepFirstContactAtARotatedCorner() {
        final ShipCollisionQuery query = unitBlock(new Matrix4d()
            .translation(0.5, 0.5, 0.5).rotateY(0.4).rotateZ(0.7).translate(-0.5, -0.5, -0.5));
        assertTrue(query.hasSupport(0.2, 0.6, 0.2, 0.8, 2.1, 0.8));
        assertFalse(query.hasSupport(0.2, 0.5543, 1.0174, 0.8, 2.0543, 1.6174));
    }

    @Test
    void faceAndCornerContactDoNotCountAsIntersection() {
        final ShipCollisionQuery query = unitBlock(new Matrix4d());
        assertFalse(query.hasSupport(0.2, 1, 0.2, 0.8, 2.5, 0.8));
        assertFalse(query.hasSupport(1, 0.4, 1, 1.6, 1.9, 1.6));
        assertTrue(query.hasSupport(0.2, 0.999, 0.2, 0.8, 2.499, 0.8));
    }

    @Test
    void upsideDownAndSidewaysBlocksHaveTheSameWorldSupport() {
        for (final double angle : new double[] {Math.PI / 2, Math.PI, -Math.PI / 2}) {
            final ShipCollisionQuery query = unitBlock(new Matrix4d()
                .translation(0.5, 0.5, 0.5).rotateZ(angle).translate(-0.5, -0.5, -0.5));
            assertTrue(query.hasSupport(0.95, 0.4, 0.2, 1.55, 1.9, 0.8));
            assertFalse(query.hasSupport(1.01, 0.4, 0.2, 1.61, 1.9, 0.8));
        }
    }

    @Test
    void rotatedBoundsAloneDoNotCountAsSupport() {
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(new Matrix4d().rotateZ(Math.PI / 4)).addBox(-2, -0.05, -0.5, 2, 0.05, 0.5);
        // Both are inside the enclosing world AABB; only the first touches the sloping board.
        assertTrue(query.hasSupport(0.9, 0.9, -0.1, 1.1, 1.2, 0.1));
        assertFalse(query.hasSupport(0.9, -1.1, -0.1, 1.1, -0.9, 0.1));
    }

    @Test
    void supportUsesScaledBlockExtents() {
        for (final double scale : new double[] {0.25, 1, 2, 8}) {
            final ShipCollisionQuery query = unitBlock(new Matrix4d().scaling(scale));
            assertTrue(query.hasSupport(scale - 0.05, scale - 0.1, 0.05,
                scale + 0.55, scale + 1.4, 0.2));
            assertFalse(query.hasSupport(scale + 0.01, scale - 0.1, 0.05,
                scale + 0.61, scale + 1.4, 0.2));
        }
    }

    @Test
    void rotatedAndNonuniformlyScaledWalkableFacesProvideSupport() {
        final Matrix4d transform = new Matrix4d().translation(-10, 20, 30)
            .rotateXYZ(0.2, -0.3, 0.4).scale(0.5, 2, 1.5);
        final ShipCollisionQuery query = unitBlock(transform);
        final Vector3d inside = transform.transformPosition(new Vector3d(0.5, 1, 0.5));
        assertTrue(query.hasSupport(inside.x - 0.01, inside.y - 0.01, inside.z - 0.01,
            inside.x + 0.01, inside.y + 0.01, inside.z + 0.01));
        final Vector3d outside = transform.transformPosition(new Vector3d(2, 2, 2));
        assertFalse(query.hasSupport(outside.x - 0.01, outside.y - 0.01, outside.z - 0.01,
            outside.x + 0.01, outside.y + 0.01, outside.z + 0.01));
    }

    @Test
    void partialBlocksKeepTheirEmptySpaces() {
        final ShipCollisionQuery query = new ShipCollisionQuery();
        final ShipCollisionQuery.ShipShapes shapes = query.addShip(new Matrix4d());
        shapes.addBox(0, 0, 0, 1, 0.5, 1);
        shapes.addBox(0, 0.5, 0.5, 1, 1, 1);
        assertFalse(query.hasSupport(0.2, 0.6, 0.1, 0.8, 0.9, 0.3));
        assertTrue(query.hasSupport(0.2, 0.6, 0.6, 0.8, 1.1, 0.9));
        assertTrue(query.hasSupport(0.2, 0.2, 0.1, 0.8, 0.6, 0.3));
    }

    @Test
    void diagonalDestinationCanBeUnsupportedEvenWhenBothAxisDestinationsAreSupported() {
        final ShipCollisionQuery query = new ShipCollisionQuery();
        final ShipCollisionQuery.ShipShapes shapes = query.addShip(new Matrix4d());
        shapes.addBox(0, 0, 0, 2, 1, 1);
        shapes.addBox(0, 0, 1, 1, 1, 2);
        assertTrue(query.hasSupport(1.1, 0.4, 0.6, 1.7, 1.9, 1.2));
        assertTrue(query.hasSupport(0.6, 0.4, 1.1, 1.2, 1.9, 1.7));
        assertFalse(query.hasSupport(1.1, 0.4, 1.1, 1.7, 1.9, 1.7));
    }

    @Test
    void narrowFenceTopSupportsTheWholeFootprint() {
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(new Matrix4d()).addBox(0.375, 0, 0.375, 0.625, 1.5, 0.625);
        assertTrue(query.hasSupport(0.6, 0.9, 0.2, 1.2, 2.4, 0.8));
        assertFalse(query.hasSupport(0.63, 0.9, 0.2, 1.23, 2.4, 0.8));
    }

    @Test
    void ceilingAboveTheProbeDoesNotProvideSupport() {
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(new Matrix4d()).addBox(0, 2, 0, 1, 3, 1);
        assertFalse(query.hasSupport(0.2, 0.4, 0.2, 0.8, 1.9, 0.8));
        assertFalse(query.hasSupport(0.2, 1.5, 0.2, 0.8, 2.5, 0.8));
    }

    @Test
    void adjacentShipsAreBothIncluded() {
        final ShipCollisionQuery query = unitBlock(new Matrix4d());
        query.addShip(new Matrix4d().translation(1, 0, 0)).addBox(0, 0, 0, 1, 1, 1);
        assertTrue(query.hasSupport(0.95, 0.4, 0.2, 1.55, 1.9, 0.8));
        assertTrue(query.hasSupport(1.4, 0.4, 0.2, 2, 1.9, 0.8));
        assertFalse(query.hasSupport(2.01, 0.4, 0.2, 2.61, 1.9, 0.8));
    }

    @Test
    void distantShipyardCoordinatesAreTransformedBeforeTesting() {
        final double shipyardX = 28_672_000;
        final double shipyardZ = -28_672_000;
        final ShipCollisionQuery query = new ShipCollisionQuery();
        query.addShip(new Matrix4d().translation(10, 50, -20).rotateY(0.4)
            .translate(-shipyardX, 0, -shipyardZ))
            .addBox(shipyardX, 0, shipyardZ, shipyardX + 1, 1, shipyardZ + 1);
        assertTrue(query.hasSupport(10.3, 50.4, -19.9, 10.9, 51.9, -19.3));
        assertFalse(query.hasSupport(12, 50.4, -19.9, 12.6, 51.9, -19.3));
    }

    @Test
    void repeatedProbesUseTheCapturedTransform() {
        final Matrix4d transform = new Matrix4d();
        final ShipCollisionQuery query = unitBlock(transform);
        transform.translate(100, 0, 0);
        for (int i = 0; i < 10; i++) {
            assertTrue(query.hasSupport(0.2, 0.4, 0.2, 0.8, 1.9, 0.8));
            assertFalse(query.hasSupport(100.2, 0.4, 0.2, 100.8, 1.9, 0.8));
        }
    }
}
