package org.valkyrienskies.mod.forge.common

import dev.engine_room.flywheel.api.event.ReloadLevelRendererEvent
import net.minecraft.commands.Commands.CommandSelection.ALL
import net.minecraft.commands.Commands.CommandSelection.INTEGRATED
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TagsUpdatedEvent
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.config.ModConfigEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.client.VSPhysicsEntityModel
import org.valkyrienskies.mod.client.VSPhysicsEntityRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_CREATOR_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.SHIP_REMOVER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_ANTIGRAV
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_CHAIR
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_FLAP
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_HINGE
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_THRUSTER
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.TEST_WING
import org.valkyrienskies.mod.common.block.TestAntigravBlock
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.block.TestFlapBlock
import org.valkyrienskies.mod.common.block.TestHingeBlock
import org.valkyrienskies.mod.common.block.TestThrusterBlock
import org.valkyrienskies.mod.common.block.TestWingBlock
import org.valkyrienskies.mod.common.blockentity.TestAntigravBlockEntity
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.blockentity.TestThrusterBlockEntity
import org.valkyrienskies.mod.common.command.VSCommands
import org.valkyrienskies.mod.common.config.DimensionParametersResolver
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSConfigUpdater
import org.valkyrienskies.mod.common.config.VSEntityHandlerDataLoader
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.item.AreaAssemblerItem
import org.valkyrienskies.mod.common.item.ConnectionCheckerItem
import org.valkyrienskies.mod.common.item.PhysicsEntityCreatorItem
import org.valkyrienskies.mod.common.item.ShipAssemblerItem
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import org.valkyrienskies.mod.common.item.ShipRemoverItem
import org.valkyrienskies.mod.compat.LoadedMods
import org.valkyrienskies.mod.compat.flywheel.ShipEmbeddingManager
import org.valkyrienskies.mod.forge.compat.dynmap.ForgeDynmapHandler
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat
import org.valkyrienskies.mod.compat.hexcasting.HexcastingCompat
import org.valkyrienskies.mod.forge.compat.epicfight.FracturedBlockStateInfoProvider
import org.valkyrienskies.mod.forge.compat.hexcasting.ForgeShipAmbit
import org.valkyrienskies.mod.util.ClientConnectivityUpdateQueue

@Mod(MOD_ID)
class ValkyrienSkiesModForge {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)
    private val ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID)
    private val BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID)
    private val TEST_CHAIR_REGISTRY: RegistryObject<Block>
    private val TEST_HINGE_REGISTRY: RegistryObject<Block>
    private val TEST_FLAP_REGISTRY: RegistryObject<Block>
    private val TEST_WING_REGISTRY: RegistryObject<Block>
    private val TEST_THRUSTER_REGISTRY: RegistryObject<Block>
    private val TEST_ANTIGRAV_REGISTRY: RegistryObject<Block>
    private val CONNECTION_CHECKER_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_CREATOR_SMALLER_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_REMOVER_ITEM_REGISTRY: RegistryObject<Item>
    private val AREA_ASSEMBLER_ITEM_REGISTRY: RegistryObject<Item>
    private val PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_MOUNTING_ENTITY_REGISTRY: RegistryObject<EntityType<ShipMountingEntity>>
    private val PHYSICS_ENTITY_TYPE_REGISTRY: RegistryObject<EntityType<VSPhysicsEntity>>
    private val SHIP_ASSEMBLER_ITEM_REGISTRY: RegistryObject<Item>
    private val TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY: RegistryObject<BlockEntityType<TestHingeBlockEntity>>
    private val TEST_THRUSTER_BLOCK_ENTITY_TYPE_REGISTRY: RegistryObject<BlockEntityType<TestThrusterBlockEntity>>
    private val TEST_ANTIGRAV_BLOCK_ENTITY_TYPE_REGISTRY: RegistryObject<BlockEntityType<TestAntigravBlockEntity>>

    init {
        val isClient = FMLEnvironment.dist.isClient

        val modBus = Bus.MOD.bus().get()
        val forgeBus = Bus.FORGE.bus().get()

        ModLoadingContext.get().apply {
            registerConfig(ModConfig.Type.SERVER, VSConfigUpdater.CORE_SERVER_SPEC, "valkyrienskies/vs-core-server.toml")
            registerConfig(ModConfig.Type.SERVER, VSConfigUpdater.SERVER_SPEC, "valkyrienskies/valkyrienskies-server.toml")
            registerConfig(ModConfig.Type.COMMON, VSConfigUpdater.COMMON_SPEC, "valkyrienskies/valkyrienskies-common.toml")
            registerConfig(ModConfig.Type.CLIENT, VSConfigUpdater.CLIENT_SPEC, "valkyrienskies/valkyrienskies-client.toml")
        }

        modBus.addListener(::onConfigLoad)
        modBus.addListener(::onConfigReload)

        ValkyrienSkiesMod.init()
        VSEntityManager.registerContraptionHandler(ContraptionShipyardEntityHandlerForge)

        BLOCKS.register(modBus)
        ITEMS.register(modBus)

        ENTITIES.register(modBus)
        BLOCK_ENTITIES.register(modBus)
        if (isClient) {
            modBus.addListener(::registerKeyBindings)
            modBus.addListener(::entityRenderers)
            modBus.addListener(::registerLayerDefinitions)
            if (LoadedMods.flywheel == LoadedMods.FlywheelVersion.V1) FlywheelCompat.initClient()
            if (ModList.get().isLoaded("flywheel")) {
                forgeBus.addListener(::registerFlywheelReload)
            }
            VSGameEvents.registriesCompleted.on {
                ClientConnectivityUpdateQueue.onRegistriesCompleted()
            }
        }
        modBus.addListener(::loadComplete)

        forgeBus.addListener(::registerCommands)
        forgeBus.addListener(::tagsUpdated)
        forgeBus.addListener(::registerResourceManagers)

        TEST_CHAIR_REGISTRY = registerBlockAndItem("test_chair") { TestChairBlock }
        TEST_HINGE_REGISTRY = registerBlockAndItem("test_hinge") { TestHingeBlock }
        TEST_FLAP_REGISTRY = registerBlockAndItem("test_flap") { TestFlapBlock }
        TEST_WING_REGISTRY = registerBlockAndItem("test_wing") { TestWingBlock }
        TEST_THRUSTER_REGISTRY = registerBlockAndItem("test_thruster") { TestThrusterBlock }
        TEST_ANTIGRAV_REGISTRY = registerBlockAndItem("test_antigrav") { TestAntigravBlock }
        SHIP_CREATOR_ITEM_REGISTRY =
            ITEMS.register("ship_creator") {
                ShipCreatorItem(Properties(),
                    { 1.0 },
                    { VSGameConfig.SERVER.minScaling })
            }
        SHIP_REMOVER_ITEM_REGISTRY =
            ITEMS.register("ship_remover") {
                ShipRemoverItem(Properties())
            }
        CONNECTION_CHECKER_ITEM_REGISTRY =
            ITEMS.register("connection_checker") {
                ConnectionCheckerItem(
                    Properties(),
                    { 1.0 },
                    { VSGameConfig.SERVER.minScaling }
                )
            }
        SHIP_CREATOR_SMALLER_ITEM_REGISTRY =
            ITEMS.register("ship_creator_smaller") {
                ShipCreatorItem(
                    Properties(),
                    { VSGameConfig.SERVER.miniShipSize },
                    { VSGameConfig.SERVER.minScaling }
                )
            }
        AREA_ASSEMBLER_ITEM_REGISTRY = ITEMS.register("area_assembler") {
            AreaAssemblerItem(
                Properties(),
                { 1.0 },
                { VSGameConfig.SERVER.minScaling }
            )
        }
        PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY =
            ITEMS.register("physics_entity_creator") {
                PhysicsEntityCreatorItem(
                    Properties(),
                )
            }

        SHIP_MOUNTING_ENTITY_REGISTRY = ENTITIES.register("ship_mounting_entity") {
            EntityType.Builder.of(
                ::ShipMountingEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .build(ResourceLocation(MOD_ID, "ship_mounting_entity").toString())
        }

        PHYSICS_ENTITY_TYPE_REGISTRY = ENTITIES.register("vs_physics_entity") {
            EntityType.Builder.of(
                ::VSPhysicsEntity,
                MobCategory.MISC
            ).sized(1f, 1f)
                .setUpdateInterval(1)
                .clientTrackingRange(10)
                .build(ResourceLocation(MOD_ID, "vs_physics_entity").toString())
        }
        modBus.addListener(::registerAttributes)


        SHIP_ASSEMBLER_ITEM_REGISTRY =
            ITEMS.register("ship_assembler") { ShipAssemblerItem(Properties()) }
        TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY = BLOCK_ENTITIES.register("test_hinge_block_entity") {
            BlockEntityType.Builder.of(::TestHingeBlockEntity, TestHingeBlock).build(null)
        }
        TEST_THRUSTER_BLOCK_ENTITY_TYPE_REGISTRY = BLOCK_ENTITIES.register("test_thruster_block_entity") {
            BlockEntityType.Builder.of(::TestThrusterBlockEntity, TestThrusterBlock).build(null)
        }
        TEST_ANTIGRAV_BLOCK_ENTITY_TYPE_REGISTRY = BLOCK_ENTITIES.register("test_antigrav_block_entity") {
            BlockEntityType.Builder.of(::TestAntigravBlockEntity, TestAntigravBlock).build(null)
        }



        // val deferredRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)
        // deferredRegister.register("general") {
        //     ValkyrienSkiesMod.createCreativeTab()
        // }
        // deferredRegister.register(modBus)

        modBus.addListener(::onTabModify)



        if (ModList.get().isLoaded("epicfight")) {
            FracturedBlockStateInfoProvider.register()
        }

        if (ModList.get().isLoaded("dynmap")) {
            ForgeDynmapHandler().register()
            forgeBus.addListener(ForgeDynmapHandler::tick)
        }

        if (ModList.get().isLoaded("hexcasting"))
            HexcastingCompat.register(ForgeShipAmbit::class.java)
    }

    private fun onTabModify(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.OP_BLOCKS) {
            event.accept(TEST_CHAIR.asItem())
            event.accept(TEST_HINGE.asItem())
            event.accept(TEST_FLAP.asItem())
            event.accept(TEST_WING.asItem())
            event.accept(TEST_THRUSTER.asItem())
            event.accept(TEST_ANTIGRAV.asItem())
            event.accept(CONNECTION_CHECKER_ITEM)
            event.accept(SHIP_CREATOR_ITEM)
            event.accept(SHIP_REMOVER_ITEM)
            event.accept(SHIP_ASSEMBLER_ITEM)
            event.accept(SHIP_CREATOR_ITEM_SMALLER)
            event.accept(AREA_ASSEMBLER_ITEM)
            event.accept(PHYSICS_ENTITY_CREATOR_ITEM)
        }
    }

    private fun onConfigLoad(event: ModConfigEvent.Loading) {
        if (event.config.modId == MOD_ID) {
            VSConfigUpdater.update(event.config)
        }
    }

    private fun onConfigReload(event: ModConfigEvent.Reloading) {
        if (event.config.modId == MOD_ID) {
            VSConfigUpdater.update(event.config)
        }
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(MassDatapackResolver.loader)
        event.addListener(VSEntityHandlerDataLoader)
        event.addListener(DimensionParametersResolver)
    }

    private fun registerKeyBindings(event: RegisterKeyMappingsEvent) {
        VSKeyBindings.clientSetup {
            event.register(it)
        }
    }

    private fun registerFlywheelReload(event: ReloadLevelRendererEvent) {
        ShipEmbeddingManager.INSTANCE.unloadAllShip()
    }

    private fun entityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(SHIP_MOUNTING_ENTITY_REGISTRY.get(), ::EmptyRenderer)
        event.registerEntityRenderer(PHYSICS_ENTITY_TYPE_REGISTRY.get(), ::VSPhysicsEntityRenderer)
    }

    private fun registerLayerDefinitions(event: EntityRenderersEvent.RegisterLayerDefinitions) {
        event.registerLayerDefinition(
            VSPhysicsEntityModel.LAYER_LOCATION,
            VSPhysicsEntityModel.Companion::createBodyLayer
        )
    }

    fun registerAttributes(event: EntityAttributeCreationEvent) {
        event.put(PHYSICS_ENTITY_TYPE_REGISTRY.get(), VSPhysicsEntity.createAttributes().build())
    }

    private fun registerBlockAndItem(registryName: String, blockSupplier: () -> Block): RegistryObject<Block> {
        val blockRegistry = BLOCKS.register(registryName, blockSupplier)
        ITEMS.register(registryName) { BlockItem(blockRegistry.get(), Properties()) }
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
        ValkyrienSkiesMod.TEST_THRUSTER = TEST_THRUSTER_REGISTRY.get()
        ValkyrienSkiesMod.TEST_ANTIGRAV = TEST_ANTIGRAV_REGISTRY.get()
        ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM = CONNECTION_CHECKER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = SHIP_CREATOR_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_REMOVER_ITEM = SHIP_REMOVER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = SHIP_ASSEMBLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = SHIP_CREATOR_SMALLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM = AREA_ASSEMBLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM = PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = SHIP_MOUNTING_ENTITY_REGISTRY.get()
        ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE = PHYSICS_ENTITY_TYPE_REGISTRY.get()
        ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE = TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY.get()
        ValkyrienSkiesMod.TEST_THRUSTER_BLOCK_ENTITY_TYPE = TEST_THRUSTER_BLOCK_ENTITY_TYPE_REGISTRY.get()
        ValkyrienSkiesMod.TEST_ANTIGRAV_BLOCK_ENTITY_TYPE = TEST_ANTIGRAV_BLOCK_ENTITY_TYPE_REGISTRY.get()
    }
}
