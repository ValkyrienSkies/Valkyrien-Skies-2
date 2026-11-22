package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4dc
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.isPointInShipyard

/**
 * The location of a hit on a ship block: world coordinates, like vanilla expects, but arithmetic with a point on the
 * same ship (typically the hit's own shipyard block pos, as in `location - blockPos`) uses the hit's shipyard
 * coordinates instead. Adding an offset keeps the result a ship hit. Reading `.x` directly can't be intercepted and
 * gives the world coordinate.
 */
class ShipHitVec3 private constructor(
    x: Double, y: Double, z: Double,
    private var cachedLocal: Vec3?,
    /** The shipyard block that was hit. */
    val shipyardPos: BlockPos
) : Vec3(x, y, z) {

    companion object {
        /** A hit at [world] whose shipyard coordinates are [local]. */
        @JvmStatic
        fun of(world: Vec3, local: Vec3, shipyardPos: BlockPos) = ShipHitVec3(world.x, world.y, world.z, local, shipyardPos)

        /** A hit at [world]; its shipyard coordinates are worked out from the ship's current transform when needed. */
        @JvmStatic
        fun lazy(world: Vec3, shipyardPos: BlockPos) = ShipHitVec3(world.x, world.y, world.z, null, shipyardPos)

        @JvmStatic
        fun inShipyard(x: Double, z: Double) = isPointInShipyard(x, z)
    }

    private val ship: Ship? by lazy(LazyThreadSafetyMode.NONE) {
        ShipAwareDistance.shipAt(shipyardPos.x + 0.5, shipyardPos.y + 0.5, shipyardPos.z + 0.5)
    }

    private fun worldToShip(): Matrix4dc? = ship?.let { (it as? ClientShip)?.renderTransform?.toModel ?: it.transform.toModel }

    /** The hit in shipyard coordinates, or null when its ship can't be found. */
    val local: Vec3?
        get() {
            cachedLocal?.let { return it }
            val m = worldToShip() ?: return null
            return m.transformPosition(Vector3d(x, y, z)).let { Vec3(it.x, it.y, it.z) }.also { cachedLocal = it }
        }

    /** Whether ([px], [py], [pz]) is a shipyard point on this hit's ship. */
    fun isOnSameShip(px: Double, py: Double, pz: Double): Boolean {
        if (!inShipyard(px, pz)) return false
        val own = ship ?: return false
        return ShipAwareDistance.shipAt(px, py, pz)?.id == own.id
    }

    private fun shifted(dx: Double, dy: Double, dz: Double): Vec3 {
        val world = Vec3(x + dx, y + dy, z + dz)
        val l = local ?: return world
        val m = worldToShip() ?: return world
        val d = m.transformDirection(Vector3d(dx, dy, dz))
        return of(world, Vec3(l.x + d.x, l.y + d.y, l.z + d.z), shipyardPos)
    }

    override fun subtract(vec: Vec3): Vec3 = subtract(vec.x, vec.y, vec.z)

    override fun subtract(px: Double, py: Double, pz: Double): Vec3 {
        if (isOnSameShip(px, py, pz)) local?.let { return Vec3(it.x - px, it.y - py, it.z - pz) }
        return Vec3(x - px, y - py, z - pz)
    }

    override fun vectorTo(vec: Vec3): Vec3 {
        if (isOnSameShip(vec.x, vec.y, vec.z)) local?.let { return Vec3(vec.x - it.x, vec.y - it.y, vec.z - it.z) }
        return Vec3(vec.x - x, vec.y - y, vec.z - z)
    }

    override fun add(vec: Vec3): Vec3 = add(vec.x, vec.y, vec.z)

    override fun add(dx: Double, dy: Double, dz: Double): Vec3 = shifted(dx, dy, dz)
}
