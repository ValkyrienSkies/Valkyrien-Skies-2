package org.valkyrienskies.mod

import org.valkyrienskies.mod.item.ShipCreatorItem
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    private val SHIP_CREATOR_ITEM: Item = ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC))

    fun init() {
        println("Hello from init")
        Registry.register(Registry.ITEM, ResourceLocation(MOD_ID, "ship_creator"), SHIP_CREATOR_ITEM)
        VSNetworking.registerVSPackets()
    }

}