package org.valkyrienskies.mod.common

import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.item.ShipCreatorItem

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    val SHIP_CREATOR_ITEM: Item = ShipCreatorItem(Item.Settings().group(ItemGroup.MISC), 1.0)
    val SHIP_CREATOR_ITEM_SMALLER: Item = ShipCreatorItem(Item.Settings().group(ItemGroup.MISC), 0.5)
    val MASS_DATAPACK_RESOLVER = MassDatapackResolver()

    fun init() {
        println("Hello from init")
        VSNetworking.registerVSPackets()
        BlockStateInfo.init()
    }
}
