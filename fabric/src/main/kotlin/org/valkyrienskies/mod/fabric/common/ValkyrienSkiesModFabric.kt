package org.valkyrienskies.mod.fabric.common

import dev.engine_room.flywheel.api.event.ReloadLevelRendererCallback
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType.SERVER_DATA
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.minecraftforge.fml.config.ModConfig
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.client.VSPhysicsEntityModel
import org.valkyrienskies.mod.client.VSPhysicsEntityRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM
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
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat
import org.valkyrienskies.mod.compat.flywheel.ShipEmbeddingManager
import org.valkyrienskies.mod.compat.hexcasting.HexcastingCompat
import org.valkyrienskies.mod.fabric.compat.dynmap.FabricDynmapHandler
import org.valkyrienskies.mod.fabric.compat.hexcasting.FabricShipAmbit
import org.valkyrienskies.mod.util.ClientConnectivityUpdateQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ValkyrienSkiesModFabric : ModInitializer {

    companion object {
        private val hasInitialized = AtomicBoolean(false)
    }

    override fun onInitialize() {
        if (hasInitialized.getAndSet(true)) return

        ForgeConfigRegistry.INSTANCE.apply {
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.SERVER, VSConfigUpdater.CORE_SERVER_SPEC, "valkyrienskies/vs-core-server.toml")
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.SERVER, VSConfigUpdater.SERVER_SPEC, "valkyrienskies/valkyrienskies-server.toml")
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.COMMON, VSConfigUpdater.COMMON_SPEC, "valkyrienskies/valkyrienskies-common.toml")
            register(ValkyrienSkiesMod.MOD_ID, ModConfig.Type.CLIENT, VSConfigUpdater.CLIENT_SPEC, "valkyrienskies/valkyrienskies-client.toml")
        }

        ModConfigEvents.reloading(ValkyrienSkiesMod.MOD_ID).register (VSConfigUpdater::update)
        ModConfigEvents.loading(ValkyrienSkiesMod.MOD_ID).register (VSConfigUpdater::update)

        ValkyrienSkiesMod.TEST_CHAIR = TestChairBlock
        ValkyrienSkiesMod.TEST_HINGE = TestHingeBlock
        ValkyrienSkiesMod.TEST_FLAP = TestFlapBlock
        ValkyrienSkiesMod.TEST_WING = TestWingBlock
        ValkyrienSkiesMod.TEST_THRUSTER = TestThrusterBlock
        ValkyrienSkiesMod.TEST_ANTIGRAV = TestAntigravBlock
        ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM = ConnectionCheckerItem(
            Properties(),
            { 1.0 },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = ShipCreatorItem(
            Properties(),
            { 1.0 },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.SHIP_REMOVER_ITEM = ShipRemoverItem(
            Properties()
        )
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = ShipAssemblerItem(Properties())
        ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM = AreaAssemblerItem(
            Properties(),
            { 1.0 },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = ShipCreatorItem(
            Properties(),
            { VSGameConfig.SERVER.miniShipSize },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM = PhysicsEntityCreatorItem(Properties())

        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = EntityType.Builder.of(
            ::ShipMountingEntity,
            MobCategory.MISC
        ).sized(.3f, .3f)
            .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())

        ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE = EntityType.Builder.of(
            ::VSPhysicsEntity,
            MobCategory.MISC
        ).sized(1f, 1f)
            .updateInterval(1)
            .clientTrackingRange(10)
            .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_physics_entity").toString())

        ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE =
            FabricBlockEntityTypeBuilder.create(::TestHingeBlockEntity, ValkyrienSkiesMod.TEST_HINGE).build()

        ValkyrienSkiesMod.TEST_THRUSTER_BLOCK_ENTITY_TYPE =
            FabricBlockEntityTypeBuilder.create(::TestThrusterBlockEntity, ValkyrienSkiesMod.TEST_THRUSTER).build()

        ValkyrienSkiesMod.TEST_ANTIGRAV_BLOCK_ENTITY_TYPE =
            FabricBlockEntityTypeBuilder.create(::TestAntigravBlockEntity, ValkyrienSkiesMod.TEST_ANTIGRAV).build()

        val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

        ValkyrienSkiesMod.init()

        if (isClient) onInitializeClient()

        VSEntityManager.registerContraptionHandler(ContraptionShipyardEntityHandlerFabric)

        registerBlockAndItem("test_chair", ValkyrienSkiesMod.TEST_CHAIR)
        registerBlockAndItem("test_hinge", ValkyrienSkiesMod.TEST_HINGE)
        registerBlockAndItem("test_flap", ValkyrienSkiesMod.TEST_FLAP)
        registerBlockAndItem("test_wing", ValkyrienSkiesMod.TEST_WING)
        registerBlockAndItem("test_thruster", ValkyrienSkiesMod.TEST_THRUSTER)
        registerBlockAndItem("test_antigrav", ValkyrienSkiesMod.TEST_ANTIGRAV)
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "connection_checker"),
            ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "area_assembler"),
            ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_assembler"),
            ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_creator"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_creator_smaller"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_remover"),
            ValkyrienSkiesMod.SHIP_REMOVER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "physics_entity_creator"),
            ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM
        )
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity"),
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_physics_entity"),
            ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE
        )
        FabricDefaultAttributeRegistry.register(ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE, VSPhysicsEntity.Companion.createAttributes())



        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "test_hinge_block_entity"),
            ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "test_thruster_block_entity"),
            ValkyrienSkiesMod.TEST_THRUSTER_BLOCK_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "test_antigrav_block_entity"),
            ValkyrienSkiesMod.TEST_ANTIGRAV_BLOCK_ENTITY_TYPE
        )

        // Registry.register(
        //     BuiltInRegistries.CREATIVE_MODE_TAB,
        //     ValkyrienSkiesMod.VS_CREATIVE_TAB,
        //     ValkyrienSkiesMod.createCreativeTab()
        // )

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.OP_BLOCKS).register { event ->
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

        CommandRegistrationCallback.EVENT.register { dispatcher ,d, _ ->
            VSCommands.registerServerCommands(dispatcher)
        }

        // registering data loaders
        val loader1 = MassDatapackResolver.loader // the get makes a new instance so get it only once
        val loader2 = VSEntityHandlerDataLoader // the get makes a new instance so get it only once
        val loader3 = DimensionParametersResolver
        ResourceManagerHelper.get(SERVER_DATA)
            .registerReloadListener(object : IdentifiableResourceReloadListener {
                override fun getFabricId(): ResourceLocation {
                    return ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_mass")
                }

                override fun reload(
                    stage: PreparationBarrier,
                    resourceManager: ResourceManager,
                    preparationsProfiler: ProfilerFiller,
                    reloadProfiler: ProfilerFiller,
                    backgroundExecutor: Executor,
                    gameExecutor: Executor
                ): CompletableFuture<Void> {
                    return loader1.reload(
                        stage, resourceManager, preparationsProfiler, reloadProfiler,
                        backgroundExecutor, gameExecutor
                    ).thenAcceptBoth(
                        loader2.reload(
                            stage, resourceManager, preparationsProfiler, reloadProfiler,
                            backgroundExecutor, gameExecutor
                        )
                    ) { _, _ -> }
                }
            })
        ResourceManagerHelper.get(SERVER_DATA)
            .registerReloadListener(object : IdentifiableResourceReloadListener {
                override fun getFabricId(): ResourceLocation {
                    return ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_dimension_parameters")
                }

                override fun reload(
                    stage: PreparationBarrier,
                    resourceManager: ResourceManager,
                    preparationsProfiler: ProfilerFiller,
                    reloadProfiler: ProfilerFiller,
                    backgroundExecutor: Executor,
                    gameExecutor: Executor
                ): CompletableFuture<Void> {
                    return loader3.reload(
                        stage, resourceManager, preparationsProfiler, reloadProfiler,
                        backgroundExecutor, gameExecutor
                    )
                }
            })
        CommonLifecycleEvents.TAGS_LOADED.register { _, _ ->
            VSGameEvents.tagsAreLoaded.emit(Unit)
        }

        if (FabricLoader.getInstance().isModLoaded("dynmap"))
            FabricDynmapHandler().register()

        if (FabricLoader.getInstance().isModLoaded("hexcasting"))
            HexcastingCompat.register(FabricShipAmbit::class.java)
    }

    /**
     * Only run on client
     */
    private fun onInitializeClient() {
        // Register the ship mounting entity renderer
        EntityRendererRegistry.register(
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        ) { context: Context ->
            EmptyRenderer(
                context
            )
        }

        EntityRendererRegistry.register(
            ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE
        ) { context: Context ->
            VSPhysicsEntityRenderer(
                context
            )
        }

        EntityModelLayerRegistry.registerModelLayer(
            VSPhysicsEntityModel.LAYER_LOCATION,
            VSPhysicsEntityModel.Companion::createBodyLayer
        )

        VSKeyBindings.clientSetup {
            KeyBindingHelper.registerKeyBinding(it)
        }

        VSGameEvents.registriesCompleted.on {
            ClientConnectivityUpdateQueue.onRegistriesCompleted()
        }

        if (LoadedMods.flywheel == LoadedMods.FlywheelVersion.V1) {
            FlywheelCompat.initClient()
        }
        if(FabricLoader.getInstance().isModLoaded("flywheel")) ReloadLevelRendererCallback.EVENT.register(
            ReloadLevelRendererCallback { event: ClientLevel? -> ShipEmbeddingManager.INSTANCE.unloadAllShip() })
    }

    private fun registerBlockAndItem(registryName: String, block: Block): Item {
        Registry.register(
            BuiltInRegistries.BLOCK, ResourceLocation(ValkyrienSkiesMod.MOD_ID, registryName),
            block
        )
        val item = BlockItem(block, Properties())
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, registryName), item)
        return item
    }
}
