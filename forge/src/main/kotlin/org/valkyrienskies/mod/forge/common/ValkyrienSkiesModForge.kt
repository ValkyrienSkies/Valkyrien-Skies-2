package org.valkyrienskies.mod.forge.common

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.fml.RegistryObject
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(ValkyrienSkiesMod.MOD_ID)
class ValkyrienSkiesModForge {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ValkyrienSkiesMod.MOD_ID)
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValkyrienSkiesMod.MOD_ID)
    private val ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, ValkyrienSkiesMod.MOD_ID)
    private val SHIP_MOUNTING_ENTITY_REGISTRY: RegistryObject<EntityType<ShipMountingEntity>>

    init {
        ValkyrienSkiesMod.init()
        VSForgeNetworking.registerForgeNetworking()
        BLOCKS.register(MOD_BUS)
        ITEMS.register(MOD_BUS)
        ENTITIES.register(MOD_BUS)
        MOD_BUS.addListener(::registerEntityRenderers)
        MOD_BUS.addListener(::loadComplete)
        FORGE_BUS.addListener(::registerResourceManagers)

        registerBlockAndItem("test_chair") { TestChairBlock }
        ITEMS.register("ship_creator") { ValkyrienSkiesMod.SHIP_CREATOR_ITEM }
        ITEMS.register("ship_creator_smaller") { ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER }
        SHIP_MOUNTING_ENTITY_REGISTRY = ENTITIES.register("ship_mounting_entity") {
            EntityType.Builder.of(
                ::ShipMountingEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())
        }
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(ValkyrienSkiesMod.MASS_DATAPACK_RESOLVER.loader)
    }

    private fun registerEntityRenderers(event: FMLClientSetupEvent) {
        RenderingRegistry.registerEntityRenderingHandler(SHIP_MOUNTING_ENTITY_REGISTRY.get(), ::EmptyRenderer)
    }

    private fun registerBlockAndItem(registryName: String, blockSupplier: () -> Block) {
        val blockRegistry = BLOCKS.register(registryName, blockSupplier)
        ITEMS.register(registryName) { BlockItem(blockRegistry.get(), Properties().tab(CreativeModeTab.TAB_MISC)) }
    }

    private fun loadComplete(event: FMLLoadCompleteEvent) {
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = SHIP_MOUNTING_ENTITY_REGISTRY.get()
    }
}
