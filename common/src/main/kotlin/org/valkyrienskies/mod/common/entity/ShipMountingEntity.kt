package org.valkyrienskies.mod.common.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3f
import org.valkyrienskies.core.api.setAttachment
import org.valkyrienskies.core.game.ships.ShipObjectServer
import org.valkyrienskies.core.networking.simple.sendToServer
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.networking.PacketPlayerDriving
import org.valkyrienskies.mod.util.VECTOR_3D_NULLABLE
import org.valkyrienskies.mod.util.defineSynced
import org.valkyrienskies.mod.util.registerSynced

class ShipMountingEntity(type: EntityType<ShipMountingEntity>, level: Level) : Entity(type, level) {

    var inShipPosition: Vector3dc?
        get() = IN_SHIP_POSITION.get(this)
        set(value) = IN_SHIP_POSITION.set(this, value)

    // Decides if this entity controlls the ship it is in.
    // Only needs to be set serverside
    var isController = false

    init {
        // Don't prevent blocks colliding with this entity from being placed
        blocksBuilding = false
        // Don't collide with terrain
        noPhysics = true
    }

    override fun tick() {
        super.tick()
        if (!level.isClientSide && passengers.isEmpty()) {
            // Kill this entity if nothing is riding it
            kill()
            return
        }
        val inShipPositionCopy = inShipPosition
        if (inShipPositionCopy != null) {
            val shipData = level.getShipManagingPos(inShipPositionCopy)
            val posInWorld: Vector3dc = if (shipData != null) {
                sendDrivingPacket()
                shipData.shipToWorld.transformPosition(inShipPositionCopy, Vector3d())
            } else {
                inShipPositionCopy
            }
            setPos(posInWorld.x(), posInWorld.y(), posInWorld.z())
        }
        reapplyPosition()
    }

    override fun remove() {
        if (this.isController && !level.isClientSide && inShipPosition != null)
            (level.getShipObjectManagingPos(inShipPosition!!) as ShipObjectServer?)
                ?.setAttachment<SeatedControllingPlayer>(null)
        super.remove()
    }

    private fun sendDrivingPacket() {
        if (!level.isClientSide) return
        val forward = VSKeyBindings.shipForward.get().isDown
        val backward = VSKeyBindings.shipBack.get().isDown
        val left = VSKeyBindings.shipLeft.get().isDown
        val right = VSKeyBindings.shipRight.get().isDown
        val up = VSKeyBindings.shipUp.get().isDown
        val down = VSKeyBindings.shipDown.get().isDown

        val impulse = Vector3f()
        impulse.z = if (forward == backward) 0.0f else if (forward) 1.0f else -1.0f
        impulse.x = if (left == right) 0.0f else if (left) 1.0f else -1.0f
        impulse.y = if (up == down) 0.0f else if (up) 1.0f else -1.0f

        PacketPlayerDriving(impulse).sendToServer()
    }

    override fun defineSynchedData() {
        registerSynced(IN_SHIP_POSITION, null)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        inShipPosition = compound.getVector3d("inShipPosition")
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        val inShipPositionCopy = inShipPosition
        if (inShipPositionCopy != null)
            compound.putVector3d("inShipPosition", inShipPositionCopy)
    }

    override fun getControllingPassenger(): Entity? {
        return if (isController) this.passengers.getOrNull(0) else null
    }

    override fun getAddEntityPacket(): Packet<*> {
        return ClientboundAddEntityPacket(this)
    }

    companion object {
        val IN_SHIP_POSITION = defineSynced<ShipMountingEntity, Vector3dc?>(VECTOR_3D_NULLABLE)
    }
}
