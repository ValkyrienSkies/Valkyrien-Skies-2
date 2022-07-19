package org.valkyrienskies.mod.common

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.item.ShipCreatorItem

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    val SHIP_CREATOR_ITEM: Item = ShipCreatorItem(Item.Properties().tab(CreativeModeTab.TAB_MISC), 1.0)
    val SHIP_CREATOR_ITEM_SMALLER: Item = ShipCreatorItem(Item.Properties().tab(CreativeModeTab.TAB_MISC), 0.5)

    fun init() {
        VSNetworking.init()
        BlockStateInfo.init()
    }
}
