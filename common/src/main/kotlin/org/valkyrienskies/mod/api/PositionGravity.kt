package org.valkyrienskies.mod.api

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.util.x
import org.valkyrienskies.core.util.y
import org.valkyrienskies.core.util.z
import org.valkyrienskies.mod.common.entity.MobWeights

class PositionGravity {
    private lateinit var instances: Map<Vector3dc?, Double>
    fun newGravityPos(
        pos: Vector3dc,
        mass: Double
    ) {
        instances + Pair(pos, mass)
    }

    fun entityEffect(
        entity: LivingEntity
    ): Vector3d {
        lateinit var forces: List<Vector3d>
        instances.forEach{
            val distance: Double =
                entity.getPosition(0.0F).distanceTo(Vec3(it.key!!.x, it.key!!.x, it.key!!.x))
            Vector3d(
                force(
                    entity.deltaMovement.x, distance,
                    MobWeights.NORMAL_PLAYER.weight, it.value,
                    entity.position().x - it.key!!.x
                ),
                force(
                    entity.deltaMovement.y, distance,
                    MobWeights.NORMAL_PLAYER.weight, it.value,
                    entity.position().y - it.key!!.y
                ),
                force(
                    entity.deltaMovement.z, distance,
                    MobWeights.NORMAL_PLAYER.weight, it.value,
                    entity.position().z - it.key!!.z
                )
            )
        }
        lateinit var totalForce: Vector3d
        forces.forEach {
            totalForce.add(it.x, it.y, it.z)
        }
        return totalForce
    }

    fun playerEffect(
        player: Player
    ): Vector3d {
        lateinit var forces: List<Vector3d>
        instances.forEach{
            val distance: Double =
                player.getPosition(0.0F).distanceTo(Vec3(it.key!!.x, it.key!!.x, it.key!!.x))
            Vector3d(
                force(
                    player.deltaMovement.x, distance,
                    MobWeights.NORMAL_PLAYER.weight, it.value,
                    player.position().x - it.key!!.x
                ),
                force(
                    player.deltaMovement.y, distance,
                    MobWeights.NORMAL_PLAYER.weight, it.value,
                    player.position().y - it.key!!.y
                ),
                force(
                    player.deltaMovement.z, distance,
                    MobWeights.NORMAL_PLAYER.weight, it.value,
                    player.position().z - it.key!!.z
                )
            )
        }
        lateinit var totalForce: Vector3d
        forces.forEach {
            totalForce.add(it.x, it.y, it.z)
        }
        return totalForce
    }
    fun shipEffect(
        ship: ServerShip
    ): Vector3d {
        lateinit var forces: List<Vector3d>
        instances.forEach {
            val distance: Double = ship.inertiaData.centerOfMassInShip.distance(it.key)
            forces + Vector3d(
                force(
                    ship.velocity.x, distance, ship.inertiaData.mass, it.value,
                    ship.inertiaData.centerOfMassInShip.x - it.key!!.x
                ),
                force(
                    ship.velocity.y, distance, ship.inertiaData.mass, it.value,
                    ship.inertiaData.centerOfMassInShip.y - it.key!!.y
                ),
                force(
                    ship.velocity.z, distance, ship.inertiaData.mass, it.value,
                    ship.inertiaData.centerOfMassInShip.z - it.key!!.z
                )
            )
        }
        lateinit var totalForce: Vector3d
        forces.forEach {
            totalForce.add(it.x, it.y, it.z)
        }
        return totalForce
    }

    private fun force(axis: Double, distance: Double, mass1: Double, mass2: Double, axisDistance: Double): Double {
        return axis - ((mass1 * axisDistance) / distance)
    }
}
