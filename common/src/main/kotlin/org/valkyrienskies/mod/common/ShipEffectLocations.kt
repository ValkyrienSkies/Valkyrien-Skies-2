package org.valkyrienskies.mod.common

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.valkyrienskies.mod.common.util.ClippedShipBox

object ShipEffectLocations {
    @JvmStatic
    fun block(pieces: List<ShipPhysicalPiece>, pos: BlockPos, x: Double, y: Double, z: Double): List<Vector3d> {
        val bounds = AABBd(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
        return pieces.mapNotNull { piece ->
            val source = piece.shipToWorld.transformPosition(Vector3d(x, y, z))
            when (ClippedShipBox.classify(bounds, piece)) {
                ClippedShipBox.Location.REMOVED -> null
                ClippedShipBox.Location.RETAINED -> source
                ClippedShipBox.Location.CUT -> {
                    val clipped = ClippedShipBox.clip(bounds, piece) ?: return@mapNotNull null
                    if (piece.clippingPlanes.all { it.x()*source.x + it.y()*source.y + it.z()*source.z + it.w() >= -1e-6 }) source
                    else clipped.vertices.fold(Vector3d()) { sum, vertex -> sum.add(vertex) }.div(clipped.vertices.size.toDouble())
                }
            }
        }
    }

    @JvmStatic
    fun nearestBlock(pieces: List<ShipPhysicalPiece>, x: Double, y: Double, z: Double, observer: Vec3): Vector3d? =
        block(pieces, BlockPos.containing(x, y, z), x, y, z).minByOrNull { it.distanceSquared(observer.x, observer.y, observer.z) }

    @JvmStatic
    fun point(pieces: List<ShipPhysicalPiece>, x: Double, y: Double, z: Double, observer: Vec3): ShipPhysicalPiece? =
        retainedUsePiece(pieces, Vec3(x, y, z), observer)
}
