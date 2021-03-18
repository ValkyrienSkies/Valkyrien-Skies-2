package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.ModInitializer
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

class ValkyrienSkiesModFabric : ModInitializer {
    override fun onInitialize() {
        ValkyrienSkiesMod.init()
        Registry.register(
            Registry.ITEM, Identifier(ValkyrienSkiesMod.MOD_ID, "ship_creator"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM
        )
        VSFabricNetworking.injectFabricPacketSenders()
    }
}
