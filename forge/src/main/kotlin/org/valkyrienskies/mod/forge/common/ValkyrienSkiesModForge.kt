package org.valkyrienskies.mod.forge.common

import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

@Mod(ValkyrienSkiesMod.MOD_ID)
class ValkyrienSkiesModForge {
    init {
        ValkyrienSkiesMod.init()
        VSForgeNetworking.registerForgeNetworking()
        ITEMS.register(FMLJavaModLoadingContext.get().modEventBus)
    }

    companion object {
        private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValkyrienSkiesMod.MOD_ID)

        init {
            ITEMS.register("ship_creator") { ValkyrienSkiesMod.SHIP_CREATOR_ITEM }
		}
    }
}