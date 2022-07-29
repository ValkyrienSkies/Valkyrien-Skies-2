package org.valkyrienskies.mod.fabric.compat.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.valkyrienskies.core.config.VSConfigClass.Companion.getRegisteredConfig
import org.valkyrienskies.core.config.VSCoreConfig
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.compat.clothconfig.VSClothConfig

class ValkyrienModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            VSClothConfig.createConfigScreenFor(
                parent,
                getRegisteredConfig(VSCoreConfig::class.java),
                getRegisteredConfig(VSGameConfig::class.java)
            )
        }
    }
}
