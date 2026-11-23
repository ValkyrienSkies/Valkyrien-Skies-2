package org.valkyrienskies.mod.common.world

import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.valkyrienskies.mod.common.allShips
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import kotlin.math.sqrt

/** Segment in one retained material image. Clip native local geometry; rank contacts in world units. */
internal class ShipRayImage(val inverse: Matrix4dc, private val forward: Matrix4dc, worldFrom: Vec3, worldTo: Vec3,
                           val physical: ShipPhysicalPiece?, private val enteredCut: Boolean = false) {
    val from = inverse.transformPosition(worldFrom.toJOML()).toMinecraft()
    val to = inverse.transformPosition(worldTo.toJOML()).toMinecraft()
    fun world(local: Vec3): Vec3 = forward.transformPosition(local.toJOML()).toMinecraft()

    fun clip(box: AABB, padding: Double, includeInside: Boolean): Vec3? {
        // Each inverse row projects a world-space radius onto one local box axis. The entity
        // bounds themselves are already expressed in native units, even on nonuniform ships.
        fun radius(x: Double, y: Double, z: Double) = padding * sqrt(x*x + y*y + z*z)
        val expanded = if (padding == 0.0) box else box.inflate(radius(inverse.m00(), inverse.m10(), inverse.m20()),
            radius(inverse.m01(), inverse.m11(), inverse.m21()), radius(inverse.m02(), inverse.m12(), inverse.m22()))
        // Intersect the shortened (retained) interval with the box. If the cut moved
        // the start into the box, the cut itself is its entry, also for glue/projectiles.
        if (enteredCut && from.x in expanded.minX..expanded.maxX && from.y in expanded.minY..expanded.maxY &&
            from.z in expanded.minZ..expanded.maxZ) return from
        return if (includeInside && expanded.contains(from)) from else expanded.clip(from, to).orElse(null)
    }
}

internal inline fun Level.forEachRayImage(ship: Ship, from: Vec3, to: Vec3, render: Boolean, visit: (ShipRayImage) -> Unit) {
    val pieces = allShips.getSplitPieces(ship.id)
    if (pieces.isEmpty()) {
        val pose = if (render && ship is ClientShip) ship.renderTransform else ship.transform
        visit(ShipRayImage(pose.worldToShip, pose.shipToWorld, from, to, null))
    } else for (physical in pieces) {
        if (physical.dimensionId != dimensionId) continue
        val image = if (render) ShipPieceRenderFrames.rendered(physical) else physical
        val retained = retainedRay(from, to, image) ?: continue
        if (retained.pointOnly && !retained.enters) continue
        visit(ShipRayImage(image.worldToShip, image.shipToWorld, retained.from, retained.to, physical, retained.enters))
    }
}
