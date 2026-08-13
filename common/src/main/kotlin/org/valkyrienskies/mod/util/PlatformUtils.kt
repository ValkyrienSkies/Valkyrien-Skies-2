package org.valkyrienskies.mod.util

interface PlatformUtils {
    fun isModLoaded(modid: String): Boolean
    fun getModVersion(modid: String): String
}
