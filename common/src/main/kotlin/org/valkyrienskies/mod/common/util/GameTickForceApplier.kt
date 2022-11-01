package org.valkyrienskies.mod.common.util

import org.joml.Vector3dc
import org.valkyrienskies.core.api.ForcesApplier
import org.valkyrienskies.core.api.ShipForcesInducer
import org.valkyrienskies.core.game.ships.PhysShip
import java.util.concurrent.ConcurrentLinkedQueue

class GameTickForceApplier : ShipForcesInducer, ForcesApplier {

    val invForces = ConcurrentLinkedQueue<Vector3dc>()
    val invTorques = ConcurrentLinkedQueue<Vector3dc>()
    val rotForces = ConcurrentLinkedQueue<Vector3dc>()
    val rotTorques = ConcurrentLinkedQueue<Vector3dc>()
    val invPosForces = ConcurrentLinkedQueue<Vector3dc>()
    val invPosPositions = ConcurrentLinkedQueue<Vector3dc>()
    var toBeStatic = false
    var toBeStaticUpdated = false

    override fun applyForces(forcesApplier: ForcesApplier, physShip: PhysShip) {
        invForces.forEach { i -> forcesApplier.applyInvariantForce(i) }
        invTorques.forEach { i -> forcesApplier.applyInvariantTorque(i) }
        rotForces.forEach { i -> forcesApplier.applyRotDependentForce(i) }
        rotTorques.forEach { i -> forcesApplier.applyRotDependentTorque(i) }
        for ((index, force) in invPosForces.withIndex()) {
            forcesApplier.applyInvariantForceToPos(force, invPosPositions.elementAt(index))
        }
        if (toBeStaticUpdated) {
            forcesApplier.setStatic(toBeStatic)
            toBeStaticUpdated = false
        }

        invForces.clear()
        invTorques.clear()
        rotForces.clear()
        rotTorques.clear()
        invPosForces.clear()
        invPosPositions.clear()
    }

    override fun applyInvariantForce(force: Vector3dc) {
        invForces.add(force)
    }

    override fun applyInvariantTorque(torque: Vector3dc) {
        invForces.add(torque)
    }

    override fun applyRotDependentForce(force: Vector3dc) {
        invForces.add(force)
    }

    override fun applyRotDependentTorque(torque: Vector3dc) {
        invForces.add(torque)
    }

    override fun applyInvariantForceToPos(force: Vector3dc, pos: Vector3dc) {
        invPosForces.add(force)
        invPosPositions.add(pos)
    }

    override fun setStatic(b: Boolean) {
        toBeStatic = b
        toBeStaticUpdated = true
    }
}
