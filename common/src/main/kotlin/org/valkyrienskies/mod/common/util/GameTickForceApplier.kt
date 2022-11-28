package org.valkyrienskies.mod.common.util

import org.joml.Vector3dc
import org.valkyrienskies.core.api.ShipForcesInducer
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.util.pollUntilEmpty
import java.util.concurrent.ConcurrentLinkedQueue

class GameTickForceApplier : ShipForcesInducer {

    private val invForces = ConcurrentLinkedQueue<Vector3dc>()
    private val invTorques = ConcurrentLinkedQueue<Vector3dc>()
    private val rotForces = ConcurrentLinkedQueue<Vector3dc>()
    private val rotTorques = ConcurrentLinkedQueue<Vector3dc>()
    private val invPosForces = ConcurrentLinkedQueue<InvForceAtPos>()

    @Volatile
    var toBeStatic = false

    @Volatile
    var toBeStaticUpdated = false

    override fun applyForces(physShip: PhysShip) {
        invForces.pollUntilEmpty(physShip::applyInvariantForce)
        invTorques.pollUntilEmpty(physShip::applyInvariantTorque)
        rotForces.pollUntilEmpty(physShip::applyRotDependentForce)
        rotTorques.pollUntilEmpty(physShip::applyRotDependentTorque)
        invPosForces.pollUntilEmpty { (force, pos) -> physShip.applyInvariantForceToPos(force, pos) }

        if (toBeStaticUpdated) {
            physShip.isStatic = toBeStatic
            toBeStaticUpdated = false
        }
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
        invPosForces.add(InvForceAtPos(force, pos))
    }

    fun setStatic(b: Boolean) {
        toBeStatic = b
        toBeStaticUpdated = true
    }

    private data class InvForceAtPos(val force: Vector3dc, val pos: Vector3dc)
}
