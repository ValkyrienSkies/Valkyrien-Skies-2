package org.valkyrienskies.mod.fabric.compat.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig

class ValkyrienModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            ValkyrienSkiesMod.api.createConfigScreenLegacy(
                parent,
                ValkyrienSkiesMod.vsCore.legacyCoreConfigClass,
                VSGameConfig::class.java
            )
        }
    }
}
