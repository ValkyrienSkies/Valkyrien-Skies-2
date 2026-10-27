package org.valkyrienskies.mod.common.util

import org.joml.Vector3d
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel

internal class PlayerShipPushDrive(
    val dimension: String,
    private val shipId: Long,
    private val pointInShip: Vector3d,
    private val inwardInShip: Vector3d,
    movement: Vector3d,
    private val eye: Vector3d,
    private val force: Double,
    private val speed: Double,
    private val reach: Double,
    private val expiresAt: Long
) {
    @Volatile private var movement = Vector3d(movement)

    fun setMovement(direction: Vector3d) {
        movement = Vector3d(direction)
    }

    @OptIn(PhysTickOnly::class)
    fun tick(level: PhysLevel, now: Long = System.nanoTime()) {
        if (level.dimension != dimension || now - expiresAt >= 0) return
        val ship = level.getShipById(shipId) ?: return
        if (ship.isStatic) return
        val point = ship.shipToWorld.transformPosition(pointInShip, Vector3d())
        val inward = ship.transform.rotation.transform(inwardInShip, Vector3d()).normalize()
        val fromPlayer = point.sub(eye, Vector3d())
        if (!point.isFinite || !inward.isFinite || fromPlayer.lengthSquared() > reach * reach ||
            fromPlayer.dot(inward) < -0.05) return
        val velocity = ShipInteractionMath.pointVelocity(ship.velocity, ship.angularVelocity, ship.transform.position, point)
        val amount = forceAmount(movement.dot(inward), velocity.dot(inward), force, speed)
        if (amount > 0.0) ship.applyWorldForce(inward.mul(amount), point)
    }

    companion object {
        internal fun forceAmount(input: Double, velocity: Double, force: Double, speed: Double): Double {
            if (!input.isFinite() || !velocity.isFinite() || !force.isFinite() || !speed.isFinite() ||
                input <= 0.0 || force <= 0.0 || speed <= 0.0) return 0.0
            // A push can add force into the face. It cannot pull or brake the ship.
            return force * input.coerceAtMost(1.0) * (1.0 - velocity / speed).coerceIn(0.0, 1.0)
        }
    }
}
