package org.valkyrienskies.mod.api

import org.valkyrienskies.core.api.ShipProvider
import kotlin.reflect.KProperty

open class ShipValueDelegate<T>(private val clazz: Class<T>, private val persistent: Boolean) {
    open operator fun getValue(thisRef: ShipProvider, property: KProperty<*>): T? =
        thisRef.ship.getAttachment(clazz)

    open operator fun setValue(thisRef: ShipProvider, property: KProperty<*>, value: T?) =
        if (persistent) thisRef.ship.saveAttachment(clazz, value) else thisRef.ship.setAttachment(clazz, value)
}

class DefaultedShipValueDelegate<T>(clazz: Class<T>, persistent: Boolean, private val default: T) :
    ShipValueDelegate<T>(clazz, persistent) {

    override operator fun getValue(thisRef: ShipProvider, property: KProperty<*>): T =
        super.getValue(thisRef, property) ?: default
}

inline fun <reified T> shipValue(default: T) =
    DefaultedShipValueDelegate(T::class.java, false, default)

inline fun <reified T> shipValue() =
    ShipValueDelegate(T::class.java, false)

inline fun <reified T> persistentShipValue(default: T) =
    DefaultedShipValueDelegate(T::class.java, true, default)

inline fun <reified T> persistentShipValue() =
    ShipValueDelegate(T::class.java, true)
