package org.valkyrienskies.mod.util

interface ModListUtil {
    fun isModLoaded(modid: String): Boolean
    fun getModVersion(modid: String): String
}
