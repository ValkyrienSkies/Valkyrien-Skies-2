package org.valkyrienskies.mod.common.render.batched

import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.valkyrienskies.mod.common.render.NativeShipPieces
import org.valkyrienskies.mod.common.util.ShipDraggingMath

class ShipPieceRenderFrameTest {
    @Test fun `native section culling considers the remote mouth and rejects empty space between mouths`() {
        val section = AABBd(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        val entrance = ShipPhysicalPiece(1L, "test", AABBd(0.0, 0.0, 8.0, 16.0, 16.0, 16.0),
            Matrix4d(), listOf(Vector4d(0.0, 0.0, 1.0, -8.0)))
        val exit = ShipPhysicalPiece(1L, "test", AABBd(100.0, 0.0, -16.0, 108.0, 16.0, 0.0),
            Matrix4d().translation(100.0, 0.0, 0.0).rotateY(Math.PI / 2), listOf(Vector4d(-1.0, 0.0, 0.0, 108.0)))
        fun visible(bounds: AABBd, camera: AABBd) =
            NativeShipPieces.sectionVisible(listOf(entrance, exit), bounds, AABBd()) {
                camera.intersectsAABB(it)
            }
        assertTrue(visible(section, AABBd(101.0, 2.0, -8.0, 102.0, 3.0, -7.0)))
        assertFalse(visible(section, AABBd(111.0, 2.0, -8.0, 112.0, 3.0, -7.0)))
        assertFalse(visible(section, AABBd(50.0, 0.0, 0.0, 52.0, 16.0, 16.0)))
        assertFalse(visible(AABBd(section).translate(0.0, 32.0, 0.0), AABBd(exit.worldBounds)))
    }

    @Test fun `first split starts each bow anchor at its previous pose in that mouth`() {
        val pivot = Vector3d(-28669951.5, 128.5, 12290047.5)
        val bow = Vector3d(pivot).add(0.0, .5, -4.0)
        val oldPose = Matrix4d().translation(3.0, 75.0, 2.0).rotateY(.3).translate(Vector3d(pivot).negate())
        val pose = Matrix4d().translation(3.2, 75.0, 1.4).rotateY(.4).translate(Vector3d(pivot).negate())
        for (chart in listOf(Matrix4d(), Matrix4d().translation(100.0, 6.0, 15.0).rotateX(.8))) {
            val matrix = chart.mul(pose, Matrix4d())
            val point = matrix.transformPosition(bow, Vector3d())
            val bounds = AABBd(point, point)
            val piece = ShipPhysicalPiece(1L, "test", bounds, matrix, listOf(Vector4d(0.0, 1.0, 0.0, 50.0)))
            val previous = piece.atPreviousPose(chart, oldPose)
            val expected = chart.transformPosition(oldPose.transformPosition(bow, Vector3d()))
            assertTrue(previous.shipToWorld.transformPosition(bow, Vector3d()).distance(expected) < 1e-7)
            assertEquals(piece.clippingPlanes, previous.clippingPlanes)
            val halfway = ShipPieceRenderFrame(previous, piece, pivot).sample(.5).shipToWorld.transformPosition(bow, Vector3d())
            assertTrue(halfway.distance(point) > .1 && halfway.distance(expected) > .1)
        }
    }


    @Test fun `small impact rotations keep clipped blocks inside the shipyard lookup bounds`() {
        val pivot = Vector3d(-28669951.5, 128.5, 12290047.5)
        fun pose(angle: Double): ShipPhysicalPiece {
            val transform = Matrix4d().translation(12.5, 77.0, 0.5).rotateX(angle)
                .translate(-pivot.x, -pivot.y, -pivot.z)
            return ShipPhysicalPiece(1L, "test", AABBd(12.0, 74.0, 0.0, 13.0, 77.0, 1.0),
                transform, listOf(Vector4d(0.0, -1.0, 0.0, 77.0)))
        }
        val frame = ShipPieceRenderFrame(pose(0.0), pose(0.001), pivot)
        val block = pivot.add(0.0, -1.0, 0.0, Vector3d())
        for (t in listOf(0.2, 0.5, 0.8)) {
            val piece = frame.sample(t)
            val world = piece.shipToWorld.transformPosition(block, Vector3d())
            val local = piece.worldToShip.transformPosition(world, Vector3d())
            assertTrue(local.distance(block) < 1e-7, "round-trip error at $t was ${local.distance(block)} blocks")
            assertTrue(AABBd(piece.worldBounds).transform(piece.worldToShip).containsPoint(block.x, block.y, block.z))
        }
    }

    @Test fun `exit block and rider rotate around the same shipyard anchor at every partial tick`() {
        val pivot = Vector3d(-28669951.5, 128.5, 12290047.5)
        val foot = pivot.add(0.0, 0.5, -2.0, Vector3d())
        fun pose(x: Double, yaw: Double) = ShipPhysicalPiece(1L, "test", AABBd(95.0, 70.0, -5.0, 106.0, 80.0, 5.0),
            Matrix4d().translation(x, 75.0, 1.0).rotateY(yaw).translate(-pivot.x, -pivot.y, -pivot.z),
            listOf(Vector4d(0.0, 0.0, -1.0, 1.0)))
        val from = pose(100.0, 0.0)
        val to = pose(101.0, 0.2)
        val frame = ShipPieceRenderFrame(from, to, pivot)
        val start = from.shipToWorld.transformPosition(foot, Vector3d())
        val end = to.shipToWorld.transformPosition(foot, Vector3d())
        for (t in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val sample = frame.sample(t)
            val expected = pose(100.0 + t, 0.2 * t).shipToWorld.transformPosition(foot, Vector3d())
            assertTrue(expected.distance(sample.shipToWorld.transformPosition(foot, Vector3d())) < 1e-7)
            assertTrue(expected.distance(ShipDraggingMath.renderPosition(start, end, from.worldToShip, to.worldToShip,
                sample.shipToWorld, t)) < 1e-7)
            assertEquals(to.clippingPlanes, sample.clippingPlanes)
        }
    }
}
