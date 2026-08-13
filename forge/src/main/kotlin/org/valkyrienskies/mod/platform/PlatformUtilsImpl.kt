package org.valkyrienskies.mod.platform

import net.minecraftforge.fml.ModList
import org.valkyrienskies.mod.util.PlatformUtils

class PlatformUtilsImpl: PlatformUtils {
    override fun isModLoaded(modid: String): Boolean {
        return ModList.get().isLoaded(modid)
    }

    override fun getModVersion(modid: String): String {
        if (!ModList.get().isLoaded(modid)) {
            return "null"
        }
        return ModList.get().getModFileById(modid).versionString()
    }
}
