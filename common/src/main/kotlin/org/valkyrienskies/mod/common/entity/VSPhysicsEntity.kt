package org.valkyrienskies.mod.common.entity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.minecraft.core.Rotations
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.Level
import net.minecraft.world.level.entity.EntityInLevelCallback
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3d
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.ships.properties.ShipInertiaData
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.internal.physics.PhysicsEntityData
import org.valkyrienskies.core.internal.physics.PhysicsEntityServer
import org.valkyrienskies.core.internal.physics.VSSphereCollisionShapeData
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.api.vsApi
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor

open class VSPhysicsEntity(type: EntityType<VSPhysicsEntity>, level: Level) : Mob(type, level) {
    // Physics data, persistent
    protected var physicsEntityData: PhysicsEntityData? = null
        private set

    // The physics entity, transient, only exists server side after this entity has been added to a world
    protected var physicsEntityServer: PhysicsEntityServer? = null
        private set

    private var lerpPos: Vector3dc? = null
    private var lerpSteps = 0

    fun setPhysicsEntityData(physicsEntityData: PhysicsEntityData) {
        if (this.physicsEntityData != null) {
            throw IllegalStateException("Cannot define physicsEntityData, its already defined!")
        }
        this.physicsEntityData = physicsEntityData
        if (!this.level().isClientSide) {
            var defaultRot = Rotations(0.0f, 0.0f, 0.0f)
            if (!this.level().isClientSide) {
                val eulerAngles = physicsEntityData.transform.shipToWorldRotation.getEulerAnglesXYZ(Vector3d())
                defaultRot = Rotations(eulerAngles.x.toFloat(), eulerAngles.y.toFloat(), eulerAngles.z.toFloat())
            }
            this.entityData.set(SHIP_ID_DATA, physicsEntityData.shipId.toString())
        }
    }

    override fun tick() {
        if (lerpPos == null) {
            lerpPos = position().toJOML()
        }
        if (!this.level().isClientSide) {
            val physicsEntityServerCopy = physicsEntityServer
            if (physicsEntityServerCopy != null) {
                val transform = physicsEntityServerCopy.shipTransform
                this.setPos(transform.positionInWorld.toMinecraft())
                val eulerAngles = transform.shipToWorldRotation.getEulerAnglesXYZ(Vector3d())
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
        this.lerpSteps = CLIENT_INTERP_STEPS
    }

    private fun tickLerp() {
        if (this.lerpSteps <= 0) {
            return
        } else if (this.lerpSteps == 1) {
            setPos(lerpPos!!.x(), lerpPos!!.y(), lerpPos!!.z())
            lerpSteps = 0
            return
        }

        val d: Double = this.x + (this.lerpPos!!.x() - this.x) / this.lerpSteps.toDouble()
        val e: Double = this.y + (this.lerpPos!!.y() - this.y) / this.lerpSteps.toDouble()
        val f: Double = this.z + (this.lerpPos!!.z() - this.z) / this.lerpSteps.toDouble()

        --this.lerpSteps
        this.setPos(d, e, f)
    }

    fun getRenderTransform(shipObjectClientWorld: VsiClientShipWorld): ShipTransform? {
        val shipIdString = entityData.get(SHIP_ID_DATA)
        if (shipIdString == "") {
            return null
        }
        val shipIdLong = shipIdString.toLong()
        return shipObjectClientWorld.getPhysEntityClientRenderTransform(shipIdLong)
    }

    override fun defineSynchedData() {
        entityData.define(SHIP_ID_DATA, "")
        super.defineSynchedData()
    }

    override fun travel(movementInput: Vec3) {
        // disable AI movement
    }

    override fun readAdditionalSaveData(compoundTag: CompoundTag) {
    }

    override fun addAdditionalSaveData(compoundTag: CompoundTag) {
    }

    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> {
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
        if (!this.level().isClientSide && physicsEntityData != null) {
            throw IllegalStateException("This entity is already loaded!")
        }
        val physicsEntityDataAsBytes: ByteArray = compoundTag.getByteArray(PHYS_DATA_NBT_KEY)
        val oldPhysicsEntityData = getMapper().readValue<PhysicsEntityData>(physicsEntityDataAsBytes)
        val newShipId = (level().shipObjectWorld as VsiServerShipWorld).allocateShipId(level().dimensionId)
        val newPhysicsEntityData = oldPhysicsEntityData.copyPhysicsEntityDataWithNewId(newShipId)
        // Change the shipId to be something new
        setPhysicsEntityData(newPhysicsEntityData)
        super.load(compoundTag)
    }

    override fun load(compoundTag: CompoundTag) {
        if (!this.level().isClientSide && physicsEntityData != null) {
            throw IllegalStateException("This entity is already loaded!")
        }
        val physicsEntityDataAsBytes: ByteArray = compoundTag.getByteArray(PHYS_DATA_NBT_KEY)
        val physicsEntityData = getMapper().readValue<PhysicsEntityData>(physicsEntityDataAsBytes)
        setPhysicsEntityData(physicsEntityData)
        super.load(compoundTag)
    }

    override fun setLevelCallback(callback: EntityInLevelCallback) {
        super.setLevelCallback(callback)
        if (!this.level().isClientSide) {
            val isNull = callback == EntityInLevelCallback.NULL
            if (!isNull) {
                // Try adding the rigid body of this entity from the world
                if (physicsEntityServer != null) {
                    throw IllegalStateException("Rigid body is already in the world!")
                }
                physicsEntityServer = (level().shipObjectWorld as VsiServerShipWorld).createPhysicsEntity(
                    physicsEntityData!!, level().dimensionId
                )
            } else {
                // Try removing the rigid body of this entity from the world
                if (physicsEntityServer == null) {
                    return
                    // throw IllegalStateException("Rigid body does not exist in the world!")
                }
                (level().shipObjectWorld as VsiServerShipWorld).deletePhysicsEntity(physicsEntityData!!.shipId)
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
        if (!this.level().isClientSide) {
            val physicsEntityServerCopy = physicsEntityServer
            if (physicsEntityServerCopy != null) {
                val newPos = Vector3d(d, e, f)
                val teleportData = vsCore.newShipTeleportData(newPos = newPos)
                (this.level().shipObjectWorld as VsiServerShipWorld).teleportPhysicsEntity(this.physicsEntityServer!!, teleportData)
            } else {
                physicsEntityData!!.transform = vsApi.newBodyTransform(
                    Vector3d(d, e, f),
                    physicsEntityData!!.transform.shipToWorldRotation,
                    Vector3d(1.0),
                    Vector3d()
                ) as ShipTransform
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

        // Use string because there is no LONG serializer by default SMH my head!
        val SHIP_ID_DATA: EntityDataAccessor<String> =
            SynchedEntityData.defineId(VSPhysicsEntity::class.java, EntityDataSerializers.STRING)

        private fun getMapper(): ObjectMapper {
            return vsCore.defaultMapper
        }

        // Without these attributes LivingEntity complains
        fun createAttributes(): AttributeSupplier.Builder {
            return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
        }

        fun createBasicSphereData(
            shipId: ShipId, transform: ShipTransform, radius: Double = 0.5, mass: Double = 10000.0
        ): PhysicsEntityData {
            val inertia = 0.4 * mass * radius * radius
            val inertiaData: ShipInertiaData = vsCore.newShipInertiaData(Vector3d(), mass, Matrix3d().scale(inertia))
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
