package org.valkyrienskies.mod.common

import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import org.valkyrienskies.mod.common.item.ShipCreatorItem

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    val SHIP_CREATOR_ITEM: Item = ShipCreatorItem(Item.Settings().group(ItemGroup.MISC))

    fun init() {
        println("Hello from init")
        VSNetworking.registerVSPackets()
    }
}
