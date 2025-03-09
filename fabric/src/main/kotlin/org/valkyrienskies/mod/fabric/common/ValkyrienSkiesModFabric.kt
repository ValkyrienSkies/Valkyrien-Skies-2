package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.loader.api.FabricLoader
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
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import org.valkyrienskies.core.apigame.VSCoreFactory
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
import org.valkyrienskies.mod.common.item.AreaAssemblerItem
import org.valkyrienskies.mod.common.item.ConnectionCheckerItem
import org.valkyrienskies.mod.common.item.PhysicsEntityCreatorItem
import org.valkyrienskies.mod.common.item.ShipAssemblerItem
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ValkyrienSkiesModFabric : ModInitializer {

    companion object {
        private val hasInitialized = AtomicBoolean(false)
    }

    override fun onInitialize() {
        if (hasInitialized.getAndSet(true)) return

        ValkyrienSkiesMod.TEST_CHAIR = TestChairBlock()
        ValkyrienSkiesMod.TEST_HINGE = TestHingeBlock()
        ValkyrienSkiesMod.TEST_FLAP = TestFlapBlock()
        ValkyrienSkiesMod.TEST_WING = TestWingBlock()
        ValkyrienSkiesMod.TEST_SPHERE = TestSphereBlock
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
            .build(ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())

        ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE = EntityType.Builder.of(
            ::VSPhysicsEntity,
            MobCategory.MISC
        ).sized(.3f, .3f)
            .updateInterval(1)
            .clientTrackingRange(10)
            .build(ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "vs_physics_entity").toString())

        ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE =
            FabricBlockEntityTypeBuilder.create(::TestHingeBlockEntity, ValkyrienSkiesMod.TEST_HINGE).build()

        val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT
        val networking = VSFabricNetworking(isClient)
        val hooks = FabricHooksImpl(networking)
        val vsCore = if (isClient) {
            VSCoreFactory.instance.newVsCoreClient(hooks)
        } else {
            VSCoreFactory.instance.newVsCoreServer(hooks)
        }

        networking.register(vsCore.hooks)

        if (isClient) onInitializeClient()

        ValkyrienSkiesMod.init(vsCore)
        // VSEntityManager.registerContraptionHandler(ContraptionShipyardEntityHandlerFabric)

        registerBlockAndItem("test_chair", ValkyrienSkiesMod.TEST_CHAIR)
        registerBlockAndItem("test_hinge", ValkyrienSkiesMod.TEST_HINGE)
        registerBlockAndItem("test_flap", ValkyrienSkiesMod.TEST_FLAP)
        registerBlockAndItem("test_wing", ValkyrienSkiesMod.TEST_WING)
        registerBlockAndItem("test_sphere", ValkyrienSkiesMod.TEST_SPHERE)
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "connection_checker"),
            ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "area_assembler"),
            ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "ship_assembler"),
            ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "ship_creator"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "ship_creator_smaller"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER
        )
        Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "physics_entity_creator"),
            ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM
        )
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity"),
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "vs_physics_entity"),
            ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE
        )
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "test_hinge_block_entity"),
            ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE
        )

        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ValkyrienSkiesMod.VS_CREATIVE_TAB,
            ValkyrienSkiesMod.createCreativeTab()
        )

        // TODO: Re-enable
        // CommandRegistrationCallback.EVENT.register { dispatcher ,d, _ ->
        //     VSCommands.registerServerCommands(dispatcher)
        // }

        // registering data loaders
        val loader1 = MassDatapackResolver.loader // the get makes a new instance so get it only once
        val loader2 = VSEntityHandlerDataLoader // the get makes a new instance so get it only once
        ResourceManagerHelper.get(SERVER_DATA)
            .registerReloadListener(object : IdentifiableResourceReloadListener {
                override fun getFabricId(): ResourceLocation {
                    return ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "vs_mass")
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
        CommonLifecycleEvents.TAGS_LOADED.register { _, _ ->
            VSGameEvents.tagsAreLoaded.emit(Unit)
        }
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

        VSKeyBindings.clientSetup {
            KeyBindingHelper.registerKeyBinding(it)
        }
    }

    private fun registerBlockAndItem(registryName: String, block: Block): Item {
        Registry.register(
            BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, registryName),
            block
        )
        val item = BlockItem(block, Properties())
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, registryName), item)
        return item
    }
}
