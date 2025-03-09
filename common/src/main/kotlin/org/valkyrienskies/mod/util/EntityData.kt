package org.valkyrienskies.mod.util

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import kotlin.reflect.KProperty

inline fun <reified T : Entity, R> defineSynced(serializer: EntityDataSerializer<R>) =
    EntityDataDelegate(SynchedEntityData.defineId(T::class.java, serializer))

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

