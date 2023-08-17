package org.valkyrienskies.mod.common.entity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.minecraft.core.Rotations
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.entity.EntityInLevelCallback
import org.joml.Matrix3d
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.ships.properties.ShipInertiaData
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.apigame.physics.PhysicsEntityData
import org.valkyrienskies.core.apigame.physics.PhysicsEntityServer
import org.valkyrienskies.core.apigame.physics.VSSphereCollisionShapeData
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.impl.game.ships.ShipInertiaDataImpl
import org.valkyrienskies.core.impl.util.serialization.VSJacksonUtil
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toMinecraft

class VSPhysicsEntity(type: EntityType<VSPhysicsEntity>, level: Level) : Entity(type, level) {
    // Physics data, persistent
    private var physicsEntityData: PhysicsEntityData? = null

    // The physics entity, transient, only exists server side after this entity has been added to a world
    private var physicsEntityServer: PhysicsEntityServer? = null

    private var clientPos: Vector3dc? = null
    private var clientRotation: Quaternionfc? = null

    val rotation: Quaternionfc
        get() {
            val rotationRaw = entityData.get(ROTATION_DATA)
            return Quaternionf().rotateXYZ(rotationRaw.x, rotationRaw.y, rotationRaw.z)
        }

    fun setPhysicsEntityData(physicsEntityData: PhysicsEntityData) {
        if (this.physicsEntityData != null) {
            throw IllegalStateException("Cannot define physicsEntityData, its already defined!")
        }
        this.physicsEntityData = physicsEntityData
    }

    override fun tick() {
        if (!this.level.isClientSide) {
            val physicsEntityServerCopy = physicsEntityServer
            if (physicsEntityServerCopy != null) {
                this.setPos(physicsEntityServerCopy.shipTransform.positionInWorld.toMinecraft())
                val eulerAngles = physicsEntityServerCopy.shipTransform.shipToWorldRotation.getEulerAnglesXYZ(Vector3d())
                this.entityData.set(
                    ROTATION_DATA, Rotations(eulerAngles.x.toFloat(), eulerAngles.y.toFloat(), eulerAngles.z.toFloat())
                )
            }
        }
        super.tick()
    }

    override fun defineSynchedData() {
        entityData.define(ROTATION_DATA, Rotations(0.0f, 0.0f, 0.0f))
    }

    override fun readAdditionalSaveData(compoundTag: CompoundTag) {
        val physicsEntityDataAsBytes: ByteArray = compoundTag.getByteArray(PHYS_DATA_NBT_KEY)
        val physicsEntityData = getMapper().readValue<PhysicsEntityData>(physicsEntityDataAsBytes)
        setPhysicsEntityData(physicsEntityData)
    }

    override fun addAdditionalSaveData(compoundTag: CompoundTag) {
        val physicsEntityDataAsBytes = getMapper().writeValueAsBytes(physicsEntityData)
        compoundTag.putByteArray(PHYS_DATA_NBT_KEY, physicsEntityDataAsBytes)
    }

    override fun getAddEntityPacket(): Packet<*> {
        return ClientboundAddEntityPacket(this)
    }

    override fun saveWithoutId(compoundTag: CompoundTag): CompoundTag {
        val physicsEntityDataAsBytes = getMapper().writeValueAsBytes(physicsEntityData)
        compoundTag.putByteArray(PHYS_DATA_NBT_KEY, physicsEntityDataAsBytes)
        return super.saveWithoutId(compoundTag)
    }

    override fun load(compoundTag: CompoundTag) {
        if (!this.level.isClientSide && physicsEntityData != null) {
            throw IllegalStateException("This entity is already loaded!")
        }
        val physicsEntityDataAsBytes: ByteArray = compoundTag.getByteArray(PHYS_DATA_NBT_KEY)
        val physicsEntityData = getMapper().readValue<PhysicsEntityData>(physicsEntityDataAsBytes)
        setPhysicsEntityData(physicsEntityData)
        super.load(compoundTag)
    }

    override fun setLevelCallback(callback: EntityInLevelCallback?) {
        super.setLevelCallback(callback)
        if (!this.level.isClientSide) {
            val isNull = (callback == null) || callback == EntityInLevelCallback.NULL
            if (!isNull) {
                // Try adding the rigid body of this entity from the world
                if (physicsEntityServer != null) {
                    throw IllegalStateException("Rigid body is already in the world!")
                }
                physicsEntityServer = (level.shipObjectWorld as ServerShipWorldCore).createPhysicsEntity(
                    physicsEntityData!!, level.dimensionId
                )
            } else {
                // Try removing the rigid body of this entity from the world
                if (physicsEntityServer == null) {
                    return
                    // throw IllegalStateException("Rigid body does not exist in the world!")
                }
                (level.shipObjectWorld as ServerShipWorldCore).deletePhysicsEntity(physicsEntityData!!.shipId)
                physicsEntityServer = null
            }
        }
    }

    companion object {
        private const val PHYS_DATA_NBT_KEY = "phys_entity_data"

        private val ROTATION_DATA: EntityDataAccessor<Rotations> =
            SynchedEntityData.defineId(VSPhysicsEntity::class.java, EntityDataSerializers.ROTATIONS)

        private fun getMapper(): ObjectMapper {
            return VSJacksonUtil.defaultMapper
        }

        fun createBasicSphereData(
            shipId: ShipId, transform: ShipTransform, radius: Double = 0.5, mass: Double = 10000.0
        ): PhysicsEntityData {
            val inertia = 0.4 * mass * radius * radius
            val inertiaData: ShipInertiaData = ShipInertiaDataImpl(Vector3d(), mass, Matrix3d().scale(inertia))
            val collisionShapeData = VSSphereCollisionShapeData(radius)
            return PhysicsEntityData(
                shipId = shipId,
                transform = transform,
                inertiaData = inertiaData,
                linearVelocity = Vector3d(),
                angularVelocity = Vector3d(),
                collisionShapeData = collisionShapeData,
                isStatic = false
            )
        }
    }
}
