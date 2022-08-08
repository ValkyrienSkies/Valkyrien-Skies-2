package org.valkyrienskies.mod.fabric.compat.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.valkyrienskies.mod.compat.clothconfig.VSClothConfig

class ValkyrienModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            VSClothConfig.createConfigScreen(parent)
        }
    }
}
