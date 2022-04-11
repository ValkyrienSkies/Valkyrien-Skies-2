package org.valkyrienskies.mod.forge.common

import net.minecraftforge.fml.common.Mod
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import thedarkcolour.kotlinforforge.forge.FORGE_BUS

@Mod(ValkyrienSkiesMod.MOD_ID)
class ValkyrienSkiesModForge {
    init {
        ValkyrienSkiesMod.init()
        VSForgeNetworking.registerForgeNetworking()
        ITEMS.register(FORGE_BUS)
    }

    companion object {
        private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValkyrienSkiesMod.MOD_ID)

        init {
            ITEMS.register("ship_creator") { ValkyrienSkiesMod.SHIP_CREATOR_ITEM }
            ITEMS.register("ship_creator_smaller") { ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER }
        }
    }
}
