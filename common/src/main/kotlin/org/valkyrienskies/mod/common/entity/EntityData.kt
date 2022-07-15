package org.valkyrienskies.mod.common.entity

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import kotlin.reflect.KProperty

inline fun <reified T : Entity, R> defineSynced(serializer: EntityDataSerializer<R>) =
    EntityDataDelegate(SynchedEntityData.defineId(T::class.java, serializer))

fun <T> Entity.registerSynced(property: EntityDataDelegate<T>, default: T) =
    this.entityData.define(property.data, default)

class EntityDataDelegate<T>(val data: EntityDataAccessor<T>) {

    fun get(thisRef: Entity?): T? {
        return thisRef?.entityData?.get(data)
    }

    fun set(thisRef: Entity?, value: T) {
        thisRef?.entityData?.set(data, value)
    }

    operator fun getValue(thisRef: Entity?, property: KProperty<*>): T? {
        return thisRef?.entityData?.get(data)
    }

    operator fun setValue(thisRef: Entity?, property: KProperty<*>, value: T) {
        thisRef?.entityData?.set(data, value)
    }
}

val VECTOR_3D: EntityDataSerializer<Vector3dc> = object : EntityDataSerializer<Vector3dc> {
    override fun write(buffer: FriendlyByteBuf, value: Vector3dc) {
        buffer.writeDouble(value.x())
        buffer.writeDouble(value.y())
        buffer.writeDouble(value.z())
    }

    override fun read(buffer: FriendlyByteBuf): Vector3dc {
        return Vector3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
    }

    override fun copy(value: Vector3dc): Vector3dc {
        return Vector3d(value)
    }
}
