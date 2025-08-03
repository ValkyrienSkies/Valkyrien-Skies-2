package org.valkyrienskies.mod.common.util

import org.joml.Vector3dc
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.apigame.joints.VSJoint
import org.valkyrienskies.core.apigame.joints.VSJointAndId
import org.valkyrienskies.core.apigame.joints.VSJointId
import org.valkyrienskies.core.apigame.world.PhysLevelCore
import org.valkyrienskies.core.util.pollUntilEmpty
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer
import java.util.function.Function

@OptIn(VsBeta::class)
class GameToPhysicsAdapter {
    private val invForces = ConcurrentLinkedQueue<Pair<ShipId, Vector3dc>>()
    private val invTorques = ConcurrentLinkedQueue<Pair<ShipId, Vector3dc>>()
    private val rotForces = ConcurrentLinkedQueue<Pair<ShipId, Vector3dc>>()
    private val rotTorques = ConcurrentLinkedQueue<Pair<ShipId, Vector3dc>>()
    private val invPosForces = ConcurrentLinkedQueue<Pair<ShipId, InvForceAtPos>>()
    private val rotPosForces = ConcurrentLinkedQueue<Pair<ShipId, InvForceAtPos>>()

    private val joints = ConcurrentHashMap<Pair<VSJoint, Consumer<VSJointId>>, Int>()
    private val updatedJoints = ConcurrentLinkedQueue<VSJointAndId>()
    private val deletedJoints = ConcurrentLinkedQueue<VSJointId>()

    private val toBeStatic = ConcurrentLinkedQueue<Pair<ShipId, Boolean>>()

    fun physTick(physLevel: PhysLevel, delta: Double) {
        invForces.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.applyInvariantForce(pair.second) }
        invTorques.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.applyInvariantTorque(pair.second) }
        rotForces.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.applyRotDependentForce(pair.second) }
        rotTorques.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.applyRotDependentTorque(pair.second) }
        invPosForces.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.applyInvariantForceToPos(pair.second.force, pair.second.pos) }
        rotPosForces.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.applyInvariantForceToPos(pair.second.force, pair.second.pos) }

        val safeJoints = HashMap(joints)
        safeJoints.forEach { newJoint, timer ->
            if (timer > 0) {
                joints[newJoint] = timer - 1
            } else {
                newJoint.second.accept((physLevel as PhysLevelCore).addJoint(newJoint.first))
                joints.remove(newJoint)
            }
        }
        updatedJoints.pollUntilEmpty { jointAndId ->
            (physLevel as PhysLevelCore).updateJoint(jointAndId.jointId, jointAndId.joint)
        }
        deletedJoints.pollUntilEmpty { jointId ->
            (physLevel as PhysLevelCore).removeJoint(jointId)
        }

        toBeStatic.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.isStatic = pair.second }
    }

    fun applyInvariantForce(ship: ShipId, force: Vector3dc) {
        invForces.add(ship to force)
    }

    fun applyInvariantTorque(ship: ShipId, torque: Vector3dc) {
        invTorques.add(ship to torque)
    }
    
    fun applyRotDependentForce(ship: ShipId, force: Vector3dc) {
        rotForces.add(ship to force)
    }

    fun applyRotDependentTorque(ship: ShipId, torque: Vector3dc) {
        rotTorques.add(ship to torque)
    }

    fun applyInvariantForceToPos(ship: ShipId, force: Vector3dc, pos: Vector3dc) {
        invPosForces.add(ship to InvForceAtPos(force, pos))
    }
    
    fun applyRotDependentForceToPos(ship: ShipId, force: Vector3dc, pos: Vector3dc) {
        rotPosForces.add(ship to InvForceAtPos(force, pos))
    }

    fun setStatic(ship: ShipId, b: Boolean) {
        toBeStatic.add(ship to b)
    }

    fun addJoint(joint: VSJoint, delay: Int = 0, function: Consumer<VSJointId>) {
        joints.put(joint to function, delay)
    }
    fun updateJoint(jointAndId: VSJointAndId) {
        updatedJoints.add(jointAndId)
    }
    fun removeJoint(jointId: VSJointId) {
        deletedJoints.add(jointId)
    }

    private data class InvForceAtPos(val force: Vector3dc, val pos: Vector3dc)
}
