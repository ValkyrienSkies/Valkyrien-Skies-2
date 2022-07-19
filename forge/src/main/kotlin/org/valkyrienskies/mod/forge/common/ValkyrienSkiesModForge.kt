package org.valkyrienskies.mod.forge.common

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.fml.RegistryObject
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(ValkyrienSkiesMod.MOD_ID)
class ValkyrienSkiesModForge {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ValkyrienSkiesMod.MOD_ID)
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValkyrienSkiesMod.MOD_ID)
    private val ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, ValkyrienSkiesMod.MOD_ID)
    private val TEST_CHAIR_REGISTRY: RegistryObject<Block>
    private val SHIP_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_CREATOR_SMALLER_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_MOUNTING_ENTITY_REGISTRY: RegistryObject<EntityType<ShipMountingEntity>>

    init {
        ValkyrienSkiesMod.init()
        VSForgeNetworking.registerForgeNetworking()
        BLOCKS.register(MOD_BUS)
        ITEMS.register(MOD_BUS)
        ENTITIES.register(MOD_BUS)
        MOD_BUS.addListener(::clientSetup)
        MOD_BUS.addListener(::loadComplete)
        FORGE_BUS.addListener(::registerResourceManagers)

        TEST_CHAIR_REGISTRY = registerBlockAndItem("test_chair") { TestChairBlock }
        SHIP_CREATOR_ITEM_REGISTRY =
            ITEMS.register("ship_creator") { ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC), 1.0) }
        SHIP_CREATOR_SMALLER_ITEM_REGISTRY =
            ITEMS.register("ship_creator_smaller") { ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC), 0.5) }
        SHIP_MOUNTING_ENTITY_REGISTRY = ENTITIES.register("ship_mounting_entity") {
            EntityType.Builder.of(
                ::ShipMountingEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())
        }
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(MassDatapackResolver.loader)
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        RenderingRegistry.registerEntityRenderingHandler(SHIP_MOUNTING_ENTITY_REGISTRY.get(), ::EmptyRenderer)
        VSKeyBindings.clientSetup {
            ClientRegistry.registerKeyBinding(it)
        }
    }

    private fun registerBlockAndItem(registryName: String, blockSupplier: () -> Block): RegistryObject<Block> {
        val blockRegistry = BLOCKS.register(registryName, blockSupplier)
        ITEMS.register(registryName) { BlockItem(blockRegistry.get(), Properties().tab(CreativeModeTab.TAB_MISC)) }
        return blockRegistry
    }

    private fun loadComplete(event: FMLLoadCompleteEvent) {
        ValkyrienSkiesMod.TEST_CHAIR = TEST_CHAIR_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = SHIP_CREATOR_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = SHIP_CREATOR_SMALLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = SHIP_MOUNTING_ENTITY_REGISTRY.get()
    }
}
