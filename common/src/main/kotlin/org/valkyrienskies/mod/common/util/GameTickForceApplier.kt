package org.valkyrienskies.mod.common.util

import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.attachments.ShipForcesInducer
import java.util.concurrent.ConcurrentLinkedQueue

class GameTickForceApplier : ShipForcesInducer {

    val invForces = ConcurrentLinkedQueue<Vector3dc>()
    val invTorques = ConcurrentLinkedQueue<Vector3dc>()
    val rotForces = ConcurrentLinkedQueue<Vector3dc>()
    val rotTorques = ConcurrentLinkedQueue<Vector3dc>()
    val invPosForces = ConcurrentLinkedQueue<Vector3dc>()
    val invPosPositions = ConcurrentLinkedQueue<Vector3dc>()
    var toBeStatic = false
    var toBeStaticUpdated = false

    override fun applyForces(physShip: PhysShip) {
        invForces.forEach { i -> physShip.applyInvariantForce(i) }
        invTorques.forEach { i -> physShip.applyInvariantTorque(i) }
        rotForces.forEach { i -> physShip.applyRotDependentForce(i) }
        rotTorques.forEach { i -> physShip.applyRotDependentTorque(i) }
        for ((index, force) in invPosForces.withIndex()) {
            physShip.applyInvariantForceToPos(force, invPosPositions.elementAt(index))
        }
        if (toBeStaticUpdated) {
            physShip.isStatic = toBeStatic
            toBeStaticUpdated = false
        }

        invForces.clear()
        invTorques.clear()
        rotForces.clear()
        rotTorques.clear()
        invPosForces.clear()
        invPosPositions.clear()
    }

    fun applyInvariantForce(force: Vector3dc) {
        invForces.add(force)
    }

    fun applyInvariantTorque(torque: Vector3dc) {
        invForces.add(torque)
    }

    fun applyRotDependentForce(force: Vector3dc) {
        invForces.add(force)
    }

    fun applyRotDependentTorque(torque: Vector3dc) {
        invForces.add(torque)
    }

    fun applyInvariantForceToPos(force: Vector3dc, pos: Vector3dc) {
        invPosForces.add(force)
        invPosPositions.add(pos)
    }

    fun setStatic(b: Boolean) {
        toBeStatic = b
        toBeStaticUpdated = true
    }
}
