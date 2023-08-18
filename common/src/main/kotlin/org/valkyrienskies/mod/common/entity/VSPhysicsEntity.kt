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
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl
import org.valkyrienskies.core.impl.game.ships.ShipInertiaDataImpl
import org.valkyrienskies.core.impl.game.ships.ShipObjectServerWorld
import org.valkyrienskies.core.impl.game.ships.ShipTransformImpl
import org.valkyrienskies.core.impl.util.serialization.VSJacksonUtil
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor

class VSPhysicsEntity(type: EntityType<VSPhysicsEntity>, level: Level) : Entity(type, level) {
    // Physics data, persistent
    private var physicsEntityData: PhysicsEntityData? = null

    // The physics entity, transient, only exists server side after this entity has been added to a world
    private var physicsEntityServer: PhysicsEntityServer? = null

    private var lastTickRotation: Quaternionfc? = null
    private var rotation: Quaternionfc? = null

    private var lerpPos: Vector3dc? = null
    private var lerpRot: Quaternionfc? = null
    private var lerpSteps = 0

    private val serverRotation: Quaternionfc
        get() {
            val rotationRaw = entityData.get(ROTATION_DATA)
            return Quaternionf().rotateXYZ(rotationRaw.x, rotationRaw.y, rotationRaw.z)
        }

    fun setPhysicsEntityData(physicsEntityData: PhysicsEntityData) {
        if (this.physicsEntityData != null) {
            throw IllegalStateException("Cannot define physicsEntityData, its already defined!")
        }
        this.physicsEntityData = physicsEntityData
        if (!this.level.isClientSide) {
            var defaultRot = Rotations(0.0f, 0.0f, 0.0f)
            if (!this.level.isClientSide) {
                val eulerAngles = physicsEntityData.transform.shipToWorldRotation.getEulerAnglesXYZ(Vector3d())
                defaultRot = Rotations(eulerAngles.x.toFloat(), eulerAngles.y.toFloat(), eulerAngles.z.toFloat())
            }
            this.entityData.set(ROTATION_DATA, defaultRot)
        }
        lastTickRotation = Quaternionf(physicsEntityData.transform.shipToWorldRotation)
        rotation = lastTickRotation
    }

    override fun tick() {
        if (lerpPos == null) {
            lerpPos = position().toJOML()
            lerpRot = serverRotation
            rotation = serverRotation
        }
        lastTickRotation = rotation
        if (!this.level.isClientSide) {
            val physicsEntityServerCopy = physicsEntityServer
            if (physicsEntityServerCopy != null) {
                val transform = physicsEntityServerCopy.shipTransform
                this.setPos(transform.positionInWorld.toMinecraft())
                val eulerAngles = transform.shipToWorldRotation.getEulerAnglesXYZ(Vector3d())
                this.entityData.set(
                    ROTATION_DATA, Rotations(eulerAngles.x.toFloat(), eulerAngles.y.toFloat(), eulerAngles.z.toFloat())
                )
                rotation = Quaternionf(transform.shipToWorldRotation)
                this.physicsEntityData!!.transform = transform
            }
            this.tryCheckInsideBlocks()
        } else {
            tickLerp()
        }
        super.tick()
    }

    override fun lerpTo(d: Double, e: Double, f: Double, g: Float, h: Float, i: Int, bl: Boolean) {
        this.lerpPos = Vector3d(d, e, f)
        this.lerpRot = serverRotation
        this.lerpSteps = CLIENT_INTERP_STEPS
    }

    private fun tickLerp() {
        if (this.lerpSteps <= 0) {
            return
        } else if (this.lerpSteps == 1) {
            setPos(lerpPos!!.x(), lerpPos!!.y(), lerpPos!!.z())
            rotation = lerpRot
            lerpSteps = 0
            return
        }

        val d: Double = this.x + (this.lerpPos!!.x() - this.x) / this.lerpSteps.toDouble()
        val e: Double = this.y + (this.lerpPos!!.y() - this.y) / this.lerpSteps.toDouble()
        val f: Double = this.z + (this.lerpPos!!.z() - this.z) / this.lerpSteps.toDouble()

        if (rotation != null) {
            rotation = rotation!!.slerp(this.lerpRot, (1.0 - (1.0 / this.lerpSteps.toDouble())).toFloat(), Quaternionf())
        } else {
            rotation = serverRotation
            lastTickRotation = rotation
        }

        --this.lerpSteps
        this.setPos(d, e, f)
    }

    fun getRenderRotation(partialTick: Float): Quaternionfc {
        if (lastTickRotation == null) {
            return serverRotation
        }
        return lastTickRotation!!.slerp(rotation!!, partialTick, Quaternionf())
    }

    override fun defineSynchedData() {
        entityData.define(ROTATION_DATA, Rotations(0.0f, 0.0f, 0.0f))
    }

    override fun readAdditionalSaveData(compoundTag: CompoundTag) {
    }

    override fun addAdditionalSaveData(compoundTag: CompoundTag) {
    }

    override fun getAddEntityPacket(): Packet<*> {
        return ClientboundAddEntityPacket(this)
    }

    override fun saveWithoutId(compoundTag: CompoundTag): CompoundTag {
        val physicsEntityDataAsBytes = getMapper().writeValueAsBytes(physicsEntityData)
        compoundTag.putByteArray(PHYS_DATA_NBT_KEY, physicsEntityDataAsBytes)
        return super.saveWithoutId(compoundTag)
    }

    // Used when teleporting through nether portals to create a new entity that's almost the same as this one
    // Note how a new shipId is generated, since this is meant to be a new copy not the exact same one
    private fun loadForTeleport(compoundTag: CompoundTag) {
        if (!this.level.isClientSide && physicsEntityData != null) {
            throw IllegalStateException("This entity is already loaded!")
        }
        val physicsEntityDataAsBytes: ByteArray = compoundTag.getByteArray(PHYS_DATA_NBT_KEY)
        val oldPhysicsEntityData = getMapper().readValue<PhysicsEntityData>(physicsEntityDataAsBytes)
        val newShipId = (level.shipObjectWorld as ShipObjectServerWorld).allocateShipId(level.dimensionId)
        val newPhysicsEntityData = PhysicsEntityData(
            shipId = newShipId,
            transform = oldPhysicsEntityData.transform,
            inertiaData = oldPhysicsEntityData.inertiaData,
            linearVelocity = oldPhysicsEntityData.linearVelocity,
            angularVelocity = oldPhysicsEntityData.angularVelocity,
            collisionShapeData = oldPhysicsEntityData.collisionShapeData,
            isStatic = oldPhysicsEntityData.isStatic,
        )
        // Change the shipId to be something new
        setPhysicsEntityData(newPhysicsEntityData)
        super.load(compoundTag)
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

    override fun shouldRenderAtSqrDistance(d: Double): Boolean {
        var e = this.boundingBox.size
        if (java.lang.Double.isNaN(e)) {
            e = 1.0
        }
        e *= 1024.0
        return d < e * e
    }

    override fun moveTo(d: Double, e: Double, f: Double, g: Float, h: Float) {
        super.moveTo(d, e, f, g, h)
        if (!this.level.isClientSide) {
            val physicsEntityServerCopy = physicsEntityServer
            if (physicsEntityServerCopy != null) {
                val newPos = Vector3d(d, e, f)
                this.entityData.set(
                    ROTATION_DATA, Rotations(0.0f, 0.0f, 0.0f)
                )
                val teleportData = ShipTeleportDataImpl(newPos = newPos)
                rotation = Quaternionf()
                (this.level.shipObjectWorld as ShipObjectServerWorld).teleportPhysicsEntity(this.physicsEntityServer!!, teleportData)
            } else {
                physicsEntityData!!.transform = ShipTransformImpl.create(
                    Vector3d(d, e, f),
                    Vector3d(),
                    physicsEntityData!!.transform.shipToWorldRotation,
                )
            }
        }
    }

    // Used when teleporting through nether portals to create a new entity that's almost the same as this one
    override fun restoreFrom(entity: Entity) {
        val compoundTag = entity.saveWithoutId(CompoundTag())
        compoundTag.remove("Dimension")
        loadForTeleport(compoundTag)
        ((this as EntityAccessor).portalCooldown) = (entity as EntityAccessor).portalCooldown
        portalEntrancePos = entity.portalEntrancePos
    }

    companion object {
        private const val PHYS_DATA_NBT_KEY = "phys_entity_data"
        private const val CLIENT_INTERP_STEPS = 3

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
