package org.valkyrienskies.mod.common.util

import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.internal.joints.VSJoint
import org.valkyrienskies.core.internal.joints.VSJointAndId
import org.valkyrienskies.core.internal.joints.VSJointId
import org.valkyrienskies.core.internal.world.VsiPhysLevel
import org.valkyrienskies.core.util.pollUntilEmpty
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

@OptIn(VsBeta::class)
class GameToPhysicsAdapter {
    private val worldForces = ConcurrentLinkedQueue<Pair<ShipId, ForceAtPos>>()
    private val worldTorques = ConcurrentLinkedQueue<Pair<ShipId, Vector3dc>>()
    private val modelForces = ConcurrentLinkedQueue<Pair<ShipId, ForceAtPos>>()
    private val modelTorques = ConcurrentLinkedQueue<Pair<ShipId, Vector3dc>>()
    private val bodyForces = ConcurrentLinkedQueue<Pair<ShipId, ForceAtPos>>()
    private val bodyTorques = ConcurrentLinkedQueue<Pair<ShipId, Vector3dc>>()
    private val worldToModelForces = ConcurrentLinkedQueue<Pair<ShipId, ForceAtPos>>()
    private val worldToBodyForces = ConcurrentLinkedQueue<Pair<ShipId, ForceAtPos>>()

    private val addedJoints = ConcurrentHashMap<Pair<VSJoint, Consumer<VSJointId>>, Int>()
    private val updatedJoints = ConcurrentLinkedQueue<VSJointAndId>()
    private val deletedJoints = ConcurrentLinkedQueue<VSJointId>()

    private val shipToJointIds = ConcurrentHashMap<Long, Set<Int>>()
    private val jointById = ConcurrentHashMap<Int, VSJoint>()

    private val toBeStatic = ConcurrentLinkedQueue<Pair<ShipId, Boolean>>()

    private val enablePairs = ConcurrentLinkedQueue<Pair<ShipId, ShipId>>()
    private val disablePairs = ConcurrentLinkedQueue<Pair<ShipId, ShipId>>()

    private val shipToLiquidOverlap = ConcurrentHashMap<Long, Double>()

    fun physTick(physLevel: PhysLevel, delta: Double) {

        worldForces.pollUntilEmpty { pair ->
            val ship = physLevel.getShipById(pair.first)
            if (pair.second.pos != null) {
                ship?.applyWorldForce(pair.second.force, pair.second.pos!!)
            } else {
                ship?.applyWorldForce(
                    pair.second.force
                )
            }
        }
        worldTorques.pollUntilEmpty { pair ->
            physLevel.getShipById(pair.first)
                ?.applyWorldTorque(pair.second)
        }
        modelForces.pollUntilEmpty { pair ->
            val ship = physLevel.getShipById(pair.first)
            if (pair.second.pos != null) {
                ship?.applyModelForce(pair.second.force, pair.second.pos!!)
            } else {
                ship?.applyModelForce(
                    pair.second.force
                )
            }
        }
        modelTorques.pollUntilEmpty { pair ->
            physLevel.getShipById(pair.first)
                ?.applyModelTorque(pair.second)
        }
        bodyForces.pollUntilEmpty { pair ->
            val ship = physLevel.getShipById(pair.first)
            if (pair.second.pos != null) {
                ship?.applyBodyForce(pair.second.force, pair.second.pos!!)
            } else {
                ship?.applyBodyForce(
                    pair.second.force
                )
            }

        }
        bodyTorques.pollUntilEmpty { pair ->
            physLevel.getShipById(pair.first)
                ?.applyBodyTorque(pair.second)
        }
        worldToModelForces.pollUntilEmpty { pair ->
            val ship = physLevel.getShipById(pair.first)
            if (pair.second.pos != null) {
                ship?.applyWorldForceToModelPos(pair.second.force, pair.second.pos!!)
            } else {
                ship?.applyWorldForceToModelPos(
                    pair.second.force
                )
            }

        }
        worldToBodyForces.pollUntilEmpty { pair ->
            val ship = physLevel.getShipById(pair.first)
            if (pair.second.pos != null) {
                ship?.applyWorldForceToBodyPos(pair.second.force, pair.second.pos!!)
            } else {
                ship?.applyWorldForceToBodyPos(
                    pair.second.force
                )
            }
        }

        // We have to have this weird queue so that we can add all our joints,
        // then update our jointById maps, then call the callbacks.
        // Otherwise, people trying to get their joint by id _in_ the callback will get null.
        val callbackQueue = ArrayList<Pair<Consumer<VSJointId>, VSJointId>>()

        val safeJoints = HashMap(addedJoints)
        safeJoints.forEach { newJoint, timer ->
            if (timer > 0) {
                addedJoints[newJoint] = timer - 1
            } else {
                callbackQueue.add(Pair(newJoint.second, (physLevel as VsiPhysLevel).addJoint(newJoint.first)))
                addedJoints.remove(newJoint)
            }
        }

        updatedJoints.pollUntilEmpty { jointAndId ->
            (physLevel as VsiPhysLevel).updateJoint(jointAndId.jointId, jointAndId.joint)
        }
        deletedJoints.pollUntilEmpty { jointId ->
            (physLevel as VsiPhysLevel).removeJoint(jointId)
        }

        // Update our joint maps - strategically placed between adding the joints, and calling the callbacks
        shipToJointIds.clear()
        jointById.clear()

        shipToJointIds.putAll((physLevel as VsiPhysLevel).getJointsByShipIds())
        jointById.putAll((physLevel as VsiPhysLevel).getAllJoints())

        // and finally... call the callbacks
        callbackQueue.forEach { (consumer, i) -> consumer.accept(i) }

        toBeStatic.pollUntilEmpty { pair -> physLevel.getShipById(pair.first)?.isStatic = pair.second }

        enablePairs.pollUntilEmpty { pair -> physLevel.enableCollisionBetween(pair.first, pair.second) }
        disablePairs.pollUntilEmpty { pair -> physLevel.disableCollisionBetween(pair.first, pair.second) }

        shipToLiquidOverlap.clear()
        physLevel.getAllPhysShips().forEach { ship ->
            shipToLiquidOverlap[ship.id] = ship.liquidOverlap
        }
    }

    /**
     * Applies a force in World Space to a ship at a World Space position. A World Space force is independent of the ship's transform, and is always global; for example, up in World Space
     * is ALWAYS (0, 1, 0) (as in, towards the sky), regardless of the ship's orientation.
     *
     * @param forceInWorld The force vector in World Space.
     * @param posInWorld The position in World Space where the force is applied. Defaults to the ship's center of mass in World Space.
     */
    fun applyWorldForce(ship: ShipId, forceInWorld: Vector3dc, posInWorld: Vector3dc?) {
        worldForces.add(ship to ForceAtPos(forceInWorld, posInWorld))
    }
    /**
     * Applies a torque in World Space to a ship at a World Space position. A World Space torque is independent of the ship's transform, and is always global; for example, up in World Space
     * is ALWAYS (0, 1, 0) (as in, towards the sky), regardless of the ship's orientation.
     *
     * @param torqueInWorld The force vector in World Space.
     */
    fun applyWorldTorque(ship: ShipId, torqueInWorld: Vector3dc) {
        worldTorques.add(ship to torqueInWorld)
    }

    /**
     * Applies a force in Model Space to a ship at a Model Space position. A Model Space force is relative to the ship's transform, meaning that it rotates and scales with the ship; for example,
     * a ship rotated on its side applying a force pointing to (0, 1, 0) in Model Space would be **perpendicular** to World Space up.
     *
     * This is useful for a Thruster or similar block that should apply a force relative to the ship's orientation.
     *
     * @param forceInShip The force vector in Model Space.
     * @param posInShip The position in Model Space where the force is applied. Defaults to the ship's center of mass in Model Space.
     */
    fun applyModelForce(ship: ShipId, forceInShip: Vector3dc, posInShip: Vector3dc?) {
        modelForces.add(ship to ForceAtPos(forceInShip, posInShip))
    }
    /**
     * Applies a torque in Model Space to a ship at a Model Space position. A Model Space torque is relative to the ship's transform, meaning that it rotates and scales with the ship.
     *
     * @param torqueInShip The torque vector in Model Space.
     * @param posInShip The position in Model Space where the torque is applied. Defaults to the ship's center of mass in Model Space.
     */
    fun applyModelTorque(ship: ShipId, torqueInShip: Vector3dc) {
        modelTorques.add(ship to torqueInShip)
    }
    /**
     * Applies a force in World Space to a ship at a Model Space position. A World Space force is independent of the ship's transform, and is always global; for example, up in World Space
     * is ALWAYS (0, 1, 0) (as in, towards the sky), regardless of the ship's orientation.
     *
     * This is useful for a balloon or similar block that should apply a force relative to the world, such as always pushing up against gravity.
     *
     * @param forceInWorld The force vector in World Space.
     * @param posInShip The position in Model Space where the force is applied. Defaults to the ship's center of mass in Model Space.
     */
    fun applyWorldForceToModelPos(ship: ShipId, forceInWorld: Vector3dc, posInShip: Vector3dc) {
        worldToModelForces.add(ship to ForceAtPos(forceInWorld, posInShip))
    }

    /**
     * Applies a force in Body Space to a ship at a Body Space position. A Body Space force is positionally relative to the ship's Center of Mass, and applies relative to the ship's transform, meaning that it rotates and scales with the ship.
     *
     * @param forceInBody The force vector in Body Space.
     * @param posInBody The position in Body Space where the force is applied. Defaults to (0,0,0), the ship's center of mass.
     */
    fun applyBodyForce(ship: ShipId, forceInBody: Vector3dc, posInBody: Vector3dc = Vector3d()) {
        bodyForces.add(ship to ForceAtPos(forceInBody, posInBody))
    }
    /**
     * Applies a torque in Body Space to a ship at a Body Space position. A Body Space torque is positionally relative to the ship's Center of Mass, and applies relative to the ship's transform, meaning that it rotates and scales with the ship.
     *
     * @param torqueInBody The force vector in Body Space.
     * @param posInBody The position in Body Space where the force is applied. Defaults to (0,0,0), the ship's center of mass.
     */
    fun applyBodyTorque(ship: ShipId, torqueInBody: Vector3dc) {
        bodyTorques.add(ship to torqueInBody)
    }
    /**
     * Applies a force in World Space to a ship at a Body Space position. A World Space force is independent of the ship's transform, and is always global; for example, up in World Space
     * is ALWAYS (0, 1, 0) (as in, towards the sky), regardless of the ship's orientation.
     *
     * @param forceInWorld The force vector in World Space.
     * @param posInBody The position in Body Space where the force is applied. Defaults to (0,0,0), the ship's center of mass.
     */
    fun applyWorldForceToBodyPos(ship: ShipId, forceInWorld: Vector3dc, posInBody: Vector3dc = Vector3d()) {
        worldToBodyForces.add(ship to ForceAtPos(forceInWorld, posInBody))
    }

    @Deprecated("Use applyWorldForceToBodyPos instead")
    fun applyInvariantForce(ship: ShipId, force: Vector3dc) {
        applyWorldForceToBodyPos(ship, force)
    }

    @Deprecated("Use applyWorldTorque instead")
    fun applyInvariantTorque(ship: ShipId, torque: Vector3dc) {
        applyWorldTorque(ship, torque)
    }

    @Deprecated("Use applyBodyForce instead")
    fun applyRotDependentForce(ship: ShipId, force: Vector3dc) {
        applyBodyForce(ship, force)
    }

    @Deprecated("Use applyBodyTorque instead")
    fun applyRotDependentTorque(ship: ShipId, torque: Vector3dc) {
        applyBodyTorque(ship, torque)
    }

    @Deprecated("Use applyWorldForceToBodyPos instead")
    fun applyInvariantForceToPos(ship: ShipId, force: Vector3dc, pos: Vector3dc) {
        applyWorldForceToBodyPos(ship, force, pos)
    }

    @Deprecated("Use applyBodyForce instead")
    fun applyRotDependentForceToPos(ship: ShipId, force: Vector3dc, pos: Vector3dc) {
        applyBodyForce(ship, force, pos)
    }

    fun setStatic(ship: ShipId, b: Boolean) {
        toBeStatic.add(ship to b)
    }

    fun addJoint(joint: VSJoint, delay: Int = 0, function: Consumer<VSJointId>) {
        addedJoints.put(joint to function, delay)
    }
    fun updateJoint(jointAndId: VSJointAndId) {
        updatedJoints.add(jointAndId)
    }
    fun removeJoint(jointId: VSJointId) {
        deletedJoints.add(jointId)
    }

    /**
     * Returns a joint by its ID.
     *
     * @param jointId The ID of the joint to retrieve.
     * @return The joint with the specified ID, or null if it does not exist.
     */
    fun getJointById(jointId: VSJointId): VSJoint? {
        return jointById[jointId]
    }

    /**
     * Returns a set containing the IDs of all joints currently attached to the ship with the specified ID.
     *
     * All returned Ids should be valid on the frame requested, but it is not advised to store this result, as it may change.
     *
     * @see [getJointById]
     */
    fun getJointsFromShip(shipId: ShipId): Set<VSJointId>? {
        return shipToJointIds[shipId]
    }

    /**
     * Retuns a map of all joints and their IDs in this PhysLevel.
     *
     * @see [getJointById]
     */
    fun getAllJoints(): Map<VSJointId, VSJoint> {
        return jointById.toMap()
    }

    /**
     * Returns a map of ShipIds to the IDs of any joints attached to them.
     *
     * @see [getJointsFromShip]
     */
    fun getJointsByShipIds(): Map<ShipId, Set<VSJointId>> {
        return shipToJointIds.toMap()
    }

    fun enableCollisionBetween(shipA: ShipId, shipB: ShipId) {
        enablePairs.add(shipA to shipB)
    }

    fun disableCollisionBetween(shipA: ShipId, shipB: ShipId) {
        disablePairs.add(shipA to shipB)
    }

    /**
     * Gets all of the ships connected to [start] with joints.
     *
     * Note: This function is mildly expensive. Try to avoid using it too often,
     * perhaps cache the return value for a few ticks.
     *
     * @param start the "root" [ShipId] to start from
     * @return A list of all connected ships (as [ShipId]s)
     */
    fun getAllConnectedShips(start: ShipId): List<ShipId> {
        val visited = mutableSetOf<ShipId>()
        val queue = ArrayDeque<ShipId>()

        queue.add(start)
        visited.add(start)

        while (queue.isNotEmpty()) {
            val currentShip = queue.removeFirst()

            val jointIds = getJointsFromShip(currentShip) ?: continue

            for (jointId in jointIds) {
                val joint = getJointById(jointId) ?: continue

                // Get the other ship attached to the joint
                val otherShip = when (currentShip) {
                    joint.shipId0 -> joint.shipId1
                    joint.shipId1 -> joint.shipId0
                    else -> null
                }

                if (otherShip != null && visited.add(otherShip)) {
                    queue.add(otherShip)
                }
            }
        }

        return visited.toList()
    }

    /**
     * Gets the percent of the ship that is overlapping a fluid, from 0 to 1.
     * Should not be null unless the `id` is not a valid ship
     */
    fun getLiquidOverlap(id: Long): Double? {
        return shipToLiquidOverlap[id]
    }

    private data class ForceAtPos(val force: Vector3dc, val pos: Vector3dc?)
}
