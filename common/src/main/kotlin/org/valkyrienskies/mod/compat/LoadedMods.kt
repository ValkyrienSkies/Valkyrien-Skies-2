package org.valkyrienskies.mod.compat

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object LoadedMods {

    @JvmStatic
    val iris by CompatInfo("net.coderbot.iris.Iris")

    @JvmStatic
    val weather2 by CompatInfo("weather2.Weather")

    class CompatInfo(private val className: String) : ReadOnlyProperty<Any?, Boolean> {
        private var isLoaded: Boolean? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            if (isLoaded == null) {
                isLoaded = try {
                    Class.forName(className, false, LoadedMods::class.java.classLoader)
                    true
                } catch (ex: ClassNotFoundException) {
                    false
                }
            }
            return isLoaded!!
        }
    }
}
