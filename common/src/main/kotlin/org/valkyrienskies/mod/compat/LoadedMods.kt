package org.valkyrienskies.mod.compat

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object LoadedMods {

    @JvmStatic
    val iris by CompatInfo("net.coderbot.iris.Iris")

    @JvmStatic
    val weather2 by CompatInfo("weather2.Weather")

    @JvmStatic
    val immersivePortals by CompatInfo("qouteall.imm_ptl.core.IPModMain")

    @JvmStatic
    val create by CompatInfo("com.simibubi.create.AllMountedDispenseItemBehaviors")

    @JvmStatic
    val oldCreate by CompatInfo("com.simibubi.create.foundation.render.AllInstanceFormats")

    @JvmStatic
    val flywheel: FlywheelVersion by lazy {
        try {
            Class.forName("dev.engine_room.flywheel.backend.FlwBackend")
            FlywheelVersion.V1
        } catch (e: ClassNotFoundException) {
            try {
                Class.forName("com.jozufozu.flywheel.Flywheel")
                FlywheelVersion.V06
            } catch (_: ClassNotFoundException) {
                FlywheelVersion.NONE
            }
        }
    }

    @JvmStatic
    val bluemap: String by lazy {
        try {
            val claz = Class.forName("de.bluecolored.bluemap.core.BlueMap")
            claz.getField("VERSION").get(null) as String
        } catch (e: ClassNotFoundException) {
            "NONE"
        }
    }

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

    enum class FlywheelVersion {
        V1,
        V06,
        NONE
    }

    enum class BluemapVersion {
        V53,
        V512,
        NONE
    }
}
