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
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.fml.ExtensionPoint
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.RegistryObject
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.core.config.VSConfigClass
import org.valkyrienskies.core.config.VSCoreConfig
import org.valkyrienskies.core.hooks.CoreHooks
import org.valkyrienskies.core.program.DaggerVSCoreClientFactory
import org.valkyrienskies.core.program.DaggerVSCoreServerFactory
import org.valkyrienskies.core.program.VSCoreModule
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.command.VSCommands
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSEntityHandlerDataLoader
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.item.ShipAssemblerItem
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import org.valkyrienskies.mod.compat.clothconfig.VSClothConfig
import java.util.function.BiFunction

@Mod(ValkyrienSkiesMod.MOD_ID)
class ValkyrienSkiesModForge {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ValkyrienSkiesMod.MOD_ID)
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValkyrienSkiesMod.MOD_ID)
    private val ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, ValkyrienSkiesMod.MOD_ID)
    private val TEST_CHAIR_REGISTRY: RegistryObject<Block>
    private val SHIP_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_CREATOR_SMALLER_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_MOUNTING_ENTITY_REGISTRY: RegistryObject<EntityType<ShipMountingEntity>>
    private val SHIP_ASSEMBLER_ITEM_REGISTRY: RegistryObject<Item>

    lateinit var ship: Ship

    init {
        CoreHooks = ForgeHooksImpl

        val isClient = FMLEnvironment.dist.isClient
        val module = VSCoreModule(ForgeHooksImpl, VSForgeNetworking())
        val vsCore = if (isClient) {
            DaggerVSCoreClientFactory.builder().vSCoreModule(module).build().client()
        } else {
            DaggerVSCoreServerFactory.builder().vSCoreModule(module).build().server()
        }

        ValkyrienSkiesMod.init(vsCore)

        val modBus = Bus.MOD.bus().get()
        val forgeBus = Bus.FORGE.bus().get()

        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        ENTITIES.register(modBus)
        modBus.addListener(::clientSetup)
        modBus.addListener(::loadComplete)

        forgeBus.addListener(::registerCommands)
        forgeBus.addListener(::registerResourceManagers)

        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY) {
            BiFunction { client, parent ->
                VSClothConfig.createConfigScreenFor(
                    parent,
                    VSConfigClass.getRegisteredConfig(VSCoreConfig::class.java),
                    VSConfigClass.getRegisteredConfig(VSGameConfig::class.java)
                )
            }
        }

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
        SHIP_ASSEMBLER_ITEM_REGISTRY =
            ITEMS.register("ship_assembler") { ShipAssemblerItem(Properties().tab(CreativeModeTab.TAB_MISC)) }
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(MassDatapackResolver.loader)
        event.addListener(VSEntityHandlerDataLoader)
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

    private fun registerCommands(event: RegisterCommandsEvent) {
        VSCommands.register(event.dispatcher)
    }

    private fun loadComplete(event: FMLLoadCompleteEvent) {
        ValkyrienSkiesMod.TEST_CHAIR = TEST_CHAIR_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = SHIP_CREATOR_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = SHIP_CREATOR_SMALLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = SHIP_MOUNTING_ENTITY_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = SHIP_ASSEMBLER_ITEM_REGISTRY.get()
    }
}
