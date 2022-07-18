package org.valkyrienskies.mod.forge.common

import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.core.hooks.VSCoreHooks
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(ValkyrienSkiesMod.MOD_ID)
class ValkyrienSkiesModForge {


    init {
        VSCoreHooks.isPhysicalClient = FMLEnvironment.dist.isClient
        VSCoreHooks.configDir = FMLPaths.CONFIGDIR.get().resolve(ValkyrienSkiesMod.MOD_ID)
        
        ValkyrienSkiesMod.init()
        VSForgeNetworking.registerForgeNetworking()
        ITEMS.register(MOD_BUS)
        FORGE_BUS.addListener(::registerResourceManagers)
    }

    companion object {
        private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValkyrienSkiesMod.MOD_ID)

        init {
            ITEMS.register("ship_creator") { ValkyrienSkiesMod.SHIP_CREATOR_ITEM }
            ITEMS.register("ship_creator_smaller") { ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER }
        }

        fun registerResourceManagers(event: AddReloadListenerEvent) {
            event.addListener(ValkyrienSkiesMod.MASS_DATAPACK_RESOLVER.loader)
        }
    }
}
