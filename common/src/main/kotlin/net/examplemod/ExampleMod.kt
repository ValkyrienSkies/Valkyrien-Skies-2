package net.examplemod

import net.examplemod.item.ShipCreatorItem
import net.examplemod.networking.VSPacketRegistry
import net.examplemod.networking.impl.VSPacketShipDataClientHandler
import net.examplemod.networking.impl.VSPacketShipDataList
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item

object ExampleMod {
    const val MOD_ID = "examplemod"

    private val SHIP_CREATOR_ITEM: Item = ShipCreatorItem(Item.Properties().tab(CreativeModeTab.TAB_MISC))

    fun init() {
        println("Hello from init")
        Registry.register(Registry.ITEM, ResourceLocation(MOD_ID, "ship_creator"), SHIP_CREATOR_ITEM)
        registerVSPackets()
    }

    private fun registerVSPackets() {
        VSPacketRegistry.registerVSPacketHandler(
            VSPacketShipDataList::class.java,
            { VSPacketShipDataList.createEmptyVSPacketShipDataList() },
            VSPacketShipDataClientHandler,
            null
        )
    }
}