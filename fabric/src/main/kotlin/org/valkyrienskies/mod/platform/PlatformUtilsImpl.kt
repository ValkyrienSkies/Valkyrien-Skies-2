package org.valkyrienskies.mod.platform

import net.fabricmc.loader.api.FabricLoader
import org.valkyrienskies.mod.util.PlatformUtils

class PlatformUtilsImpl: PlatformUtils {
    override fun isModLoaded(modid: String): Boolean {
        return FabricLoader.getInstance().isModLoaded(modid)
    }

    override fun getModVersion(modid: String): String {
        if (!FabricLoader.getInstance().isModLoaded(modid)) {
            return "null"
        }
        return FabricLoader.getInstance().getModContainer(modid).get().metadata.version.toString()
    }
}
