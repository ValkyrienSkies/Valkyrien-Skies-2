package org.valkyrienskies.mod.compat

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object LoadedMods {

    @JvmStatic
    val iris by CompatInfo("net.coderbot.iris.Iris")

    class CompatInfo(private val className: String) : ReadOnlyProperty<Any?, Boolean> {
        private var isLoaded: Boolean? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            if (isLoaded == null) {
                try {
                    Class.forName(className)
                    isLoaded = true
                } catch (ex: ClassNotFoundException) {
                    ex.printStackTrace()
                    isLoaded = false
                }
            }
            return isLoaded!!
        }
    }
}
