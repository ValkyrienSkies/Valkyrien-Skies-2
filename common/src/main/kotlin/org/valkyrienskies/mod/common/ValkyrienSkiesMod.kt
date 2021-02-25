package org.valkyrienskies.mod.common

import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import org.valkyrienskies.mod.common.item.ShipCreatorItem

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    private val SHIP_CREATOR_ITEM: Item = ShipCreatorItem(Item.Settings().group(ItemGroup.MISC))

    fun init() {
        println("Hello from init")
        Registry.register(Registry.ITEM, Identifier(MOD_ID, "ship_creator"), SHIP_CREATOR_ITEM)
        VSNetworking.registerVSPackets()
    }

}