package org.valkyrienskies.mod.common.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.mod.common.getShipManagingPos

class ShipMountingEntity(type: EntityType<ShipMountingEntity>, level: Level) : Entity(type, level) {

    var inShipPosition: Vector3dc?
        get() = IN_SHIP_POSITION.get(this)
        set(value) = IN_SHIP_POSITION.set(this, value)

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
                shipData.shipToWorld.transformPosition(inShipPositionCopy, Vector3d())
            } else {
                inShipPositionCopy
            }
            setPos(posInWorld.x(), posInWorld.y(), posInWorld.z())
        }
        reapplyPosition()
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
        return this.passengers.getOrNull(0)
    }

    override fun getAddEntityPacket(): Packet<*> {
        return ClientboundAddEntityPacket(this)
    }

    override fun addPassenger(passenger: Entity) {
        super.addPassenger(passenger)
        if (passenger is ServerPlayer) {
            passenger.setPos(this.x, this.y, this.z)
        }
    }

    companion object {
        val IN_SHIP_POSITION = defineSynced<ShipMountingEntity, Vector3dc?>(VECTOR_3D_NULLABLE)
    }
}
