package org.valkyrienskies.mod.common.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import org.joml.Vector3d

class ShipMountingEntity(type: EntityType<ShipMountingEntity>, level: Level) : Entity(type, level) {

    private lateinit var inShipPosition: Vector3d

    init {
        blocksBuilding = true
    }

    // We discard any position assignments as long we are on a ship
    override fun setPosRaw(x: Double, y: Double, z: Double) {
        return
    }

    override fun tick() {
        super.tick()
        reapplyPosition()
    }

    override fun defineSynchedData() {}

    override fun readAdditionalSaveData(compound: CompoundTag) {
        inShipPosition = compound.getVector3d("inShipPosition")
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        compound.putVector3d("inShipPosition", inShipPosition)
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
}
