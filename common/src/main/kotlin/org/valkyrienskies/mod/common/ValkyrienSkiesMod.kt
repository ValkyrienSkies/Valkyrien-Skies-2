package org.valkyrienskies.mod.common

import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import org.valkyrienskies.core.config.VSConfigClass
import org.valkyrienskies.core.config.VSCoreConfig
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import org.valkyrienskies.mod.common.networking.impl.VSGameNetworking

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    val SHIP_CREATOR_ITEM: Item = ShipCreatorItem(Item.Properties().tab(CreativeModeTab.TAB_MISC), 1.0)
    val SHIP_CREATOR_ITEM_SMALLER: Item = ShipCreatorItem(Item.Properties().tab(CreativeModeTab.TAB_MISC), 0.5)
    val MASS_DATAPACK_RESOLVER = MassDatapackResolver()

    @JvmStatic
    var currentServer: MinecraftServer? = null

    fun init() {
        println("Hello from init")
        VSNetworking.init()
        VSGameNetworking.registerHandlers()
        BlockStateInfo.init()

        VSConfigClass.registerConfig("vs_core", VSCoreConfig::class.java)
        VSConfigClass.registerConfig("vs", VSGameConfig::class.java)
    }
}
