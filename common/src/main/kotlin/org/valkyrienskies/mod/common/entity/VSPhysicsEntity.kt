package org.valkyrienskies.mod.common.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.valkyrienskies.core.api.ships.properties.ShipId

class VSPhysicsEntity(type: EntityType<VSPhysicsEntity>, level: Level) : Entity(type, level) {
    private var shipId: ShipId? = null
    var rotation: Quaternionfc = Quaternionf()

    fun setShipId(shipId: ShipId) {
        if (this.shipId != null) {
            throw IllegalStateException("Cannot define shipId, its already defined!")
        }
        this.shipId = shipId
    }

    override fun defineSynchedData() {
        // Naw, send pos/rotation using physics packets
    }

    override fun readAdditionalSaveData(compoundTag: CompoundTag) {
        this.shipId = compoundTag.getLong("shipId")
    }

    override fun addAdditionalSaveData(compoundTag: CompoundTag) {
        compoundTag.putLong("shipId", shipId!!)
    }

    override fun getAddEntityPacket(): Packet<*> {
        return ClientboundAddEntityPacket(this)
    }

    override fun saveWithoutId(compoundTag: CompoundTag): CompoundTag {
        compoundTag.putLong("shipId", shipId!!)
        compoundTag.putFloat("rotationX", rotation.x())
        compoundTag.putFloat("rotationY", rotation.y())
        compoundTag.putFloat("rotationZ", rotation.z())
        compoundTag.putFloat("rotationW", rotation.w())
        return super.saveWithoutId(compoundTag)
    }

    override fun load(compoundTag: CompoundTag) {
        this.shipId = compoundTag.getLong("shipId")
        val rotationX = compoundTag.getFloat("rotationX")
        val rotationY = compoundTag.getFloat("rotationY")
        val rotationZ = compoundTag.getFloat("rotationZ")
        val rotationW = compoundTag.getFloat("rotationW")
        this.rotation = Quaternionf(rotationX, rotationY, rotationZ, rotationW)
        super.load(compoundTag)
    }
}
