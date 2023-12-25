package org.valkyrienskies.mod.forge.common

import net.minecraft.commands.Commands.CommandSelection.ALL
import net.minecraft.commands.Commands.CommandSelection.INTEGRATED
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayerGameMode
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TagsUpdatedEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.valkyrienskies.core.apigame.VSCoreFactory
import org.valkyrienskies.core.impl.config.VSConfigClass
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.client.VSPhysicsEntityRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.block.TestFlapBlock
import org.valkyrienskies.mod.common.block.TestHingeBlock
import org.valkyrienskies.mod.common.block.TestSphereBlock
import org.valkyrienskies.mod.common.block.TestWingBlock
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.command.VSCommands
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSEntityHandlerDataLoader
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.item.PhysicsEntityCreatorItem
import org.valkyrienskies.mod.common.item.ShipAssemblerItem
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import org.valkyrienskies.mod.compat.clothconfig.VSClothConfig

@Mod(ValkyrienSkiesMod.MOD_ID)
class ValkyrienSkiesModForge {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ValkyrienSkiesMod.MOD_ID)
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ValkyrienSkiesMod.MOD_ID)
    private val ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ValkyrienSkiesMod.MOD_ID)
    private val BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ValkyrienSkiesMod.MOD_ID)
    private val TEST_CHAIR_REGISTRY: RegistryObject<Block>
    private val TEST_HINGE_REGISTRY: RegistryObject<Block>
    private val TEST_FLAP_REGISTRY: RegistryObject<Block>
    private val TEST_WING_REGISTRY: RegistryObject<Block>
    private val TEST_SPHERE_REGISTRY: RegistryObject<Block>
    private val SHIP_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_CREATOR_SMALLER_ITEM_REGISTRY: RegistryObject<Item>
    private val PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_MOUNTING_ENTITY_REGISTRY: RegistryObject<EntityType<ShipMountingEntity>>
    private val PHYSICS_ENTITY_TYPE_REGISTRY: RegistryObject<EntityType<VSPhysicsEntity>>
    private val SHIP_ASSEMBLER_ITEM_REGISTRY: RegistryObject<Item>
    private val TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY: RegistryObject<BlockEntityType<TestHingeBlockEntity>>

    init {
        val isClient = FMLEnvironment.dist.isClient
        val vsCore = if (isClient) {
            VSCoreFactory.instance.newVsCoreClient(ForgeHooksImpl)
        } else {
            VSCoreFactory.instance.newVsCoreServer(ForgeHooksImpl)
        }

        VSForgeNetworking.registerPacketHandlers(vsCore.hooks)

        ValkyrienSkiesMod.init(vsCore)
        VSEntityManager.registerContraptionHandler(ContraptionShipyardEntityHandlerForge)

        val modBus = Bus.MOD.bus().get()
        val forgeBus = Bus.FORGE.bus().get()

        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        ENTITIES.register(modBus)
        BLOCK_ENTITIES.register(modBus)
        if (isClient) {
            modBus.addListener(::registerKeyBindings)
            modBus.addListener(::entityRenderers)
        }
        modBus.addListener(::loadComplete)

        forgeBus.addListener(::registerCommands)
        forgeBus.addListener(::tagsUpdated)
        forgeBus.addListener(::registerResourceManagers)

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory::class.java) {
            ConfigScreenHandler.ConfigScreenFactory { _, parent ->
                VSClothConfig.createConfigScreenFor(
                    parent,
                    VSConfigClass.getRegisteredConfig(VSCoreConfig::class.java),
                    VSConfigClass.getRegisteredConfig(VSGameConfig::class.java)
                )
            }
        }

        TEST_CHAIR_REGISTRY = registerBlockAndItem("test_chair") { TestChairBlock }
        TEST_HINGE_REGISTRY = registerBlockAndItem("test_hinge") { TestHingeBlock }
        TEST_FLAP_REGISTRY = registerBlockAndItem("test_flap") { TestFlapBlock }
        TEST_WING_REGISTRY = registerBlockAndItem("test_wing") { TestWingBlock }
        TEST_SPHERE_REGISTRY = registerBlockAndItem("test_sphere") { TestSphereBlock }
        SHIP_CREATOR_ITEM_REGISTRY =
            ITEMS.register("ship_creator") {
                ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC),
                    { 1.0 },
                    { VSGameConfig.SERVER.minScaling })
            }
        SHIP_CREATOR_SMALLER_ITEM_REGISTRY =
            ITEMS.register("ship_creator_smaller") {
                ShipCreatorItem(
                    Properties().tab(CreativeModeTab.TAB_MISC),
                    { VSGameConfig.SERVER.miniShipSize },
                    { VSGameConfig.SERVER.minScaling }
                )
            }

        PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY =
            ITEMS.register("physics_entity_creator") {
                PhysicsEntityCreatorItem(
                    Properties().tab(CreativeModeTab.TAB_MISC),
                )
            }

        SHIP_MOUNTING_ENTITY_REGISTRY = ENTITIES.register("ship_mounting_entity") {
            EntityType.Builder.of(
                ::ShipMountingEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())
        }

        PHYSICS_ENTITY_TYPE_REGISTRY = ENTITIES.register("vs_physics_entity") {
            EntityType.Builder.of(
                ::VSPhysicsEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .setUpdateInterval(1)
                .clientTrackingRange(10)
                .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_physics_entity").toString())
        }

        SHIP_ASSEMBLER_ITEM_REGISTRY =
            ITEMS.register("ship_assembler") { ShipAssemblerItem(Properties().tab(CreativeModeTab.TAB_MISC)) }
        TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY = BLOCK_ENTITIES.register("test_hinge_block_entity") {
            BlockEntityType.Builder.of(::TestHingeBlockEntity, TestHingeBlock).build(null)
        }
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(MassDatapackResolver.loader)
        event.addListener(VSEntityHandlerDataLoader)
    }

    private fun registerKeyBindings(event: RegisterKeyMappingsEvent) {
        VSKeyBindings.clientSetup {
            event.register(it)
        }
    }

    private fun entityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(SHIP_MOUNTING_ENTITY_REGISTRY.get(), ::EmptyRenderer)
        event.registerEntityRenderer(PHYSICS_ENTITY_TYPE_REGISTRY.get(), ::VSPhysicsEntityRenderer)
    }

    private fun registerBlockAndItem(registryName: String, blockSupplier: () -> Block): RegistryObject<Block> {
        val blockRegistry = BLOCKS.register(registryName, blockSupplier)
        ITEMS.register(registryName) { BlockItem(blockRegistry.get(), Properties().tab(CreativeModeTab.TAB_MISC)) }
        return blockRegistry
    }

    private fun registerCommands(event: RegisterCommandsEvent) {
        VSCommands.registerServerCommands(event.dispatcher)

        if (event.commandSelection == ALL || event.commandSelection == INTEGRATED) {
            VSCommands.registerClientCommands(event.dispatcher)
        }
    }

    private fun tagsUpdated(event: TagsUpdatedEvent) {
        VSGameEvents.tagsAreLoaded.emit(Unit)
    }

    private fun loadComplete(event: FMLLoadCompleteEvent) {
        ValkyrienSkiesMod.TEST_CHAIR = TEST_CHAIR_REGISTRY.get()
        ValkyrienSkiesMod.TEST_HINGE = TEST_HINGE_REGISTRY.get()
        ValkyrienSkiesMod.TEST_FLAP = TEST_FLAP_REGISTRY.get()
        ValkyrienSkiesMod.TEST_WING = TEST_WING_REGISTRY.get()
        ValkyrienSkiesMod.TEST_SPHERE = TEST_SPHERE_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = SHIP_CREATOR_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = SHIP_CREATOR_SMALLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = SHIP_MOUNTING_ENTITY_REGISTRY.get()
        ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE = PHYSICS_ENTITY_TYPE_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = SHIP_ASSEMBLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE = TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY.get()
    }
}
