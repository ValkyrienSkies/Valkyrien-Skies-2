package org.valkyrienskies.mod.fabric.compat.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.valkyrienskies.core.config.VSConfigClass.Companion.getOrRegisterConfig
import org.valkyrienskies.core.config.VSCoreConfig
import org.valkyrienskies.mod.compat.clothconfig.createConfigScreenFor

class ValkyrienModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            createConfigScreenFor(getOrRegisterConfig("vs_core", VSCoreConfig::class.java), parent)
        }
    }
}
