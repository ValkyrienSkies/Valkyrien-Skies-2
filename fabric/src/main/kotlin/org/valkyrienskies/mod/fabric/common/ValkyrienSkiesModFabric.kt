package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType.SERVER_DATA
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import org.valkyrienskies.core.apigame.VSCoreFactory
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.block.TestFlapBlock
import org.valkyrienskies.mod.common.block.TestHingeBlock
import org.valkyrienskies.mod.common.block.TestWingBlock
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.command.VSCommands
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSEntityHandlerDataLoader
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.hooks.VSGameEvents
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

        ValkyrienSkiesMod.TEST_CHAIR = TestChairBlock
        ValkyrienSkiesMod.TEST_HINGE = TestHingeBlock
        ValkyrienSkiesMod.TEST_FLAP = TestFlapBlock
        ValkyrienSkiesMod.TEST_WING = TestWingBlock
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = ShipCreatorItem(
            Properties().tab(CreativeModeTab.TAB_MISC),
            { 1.0 },
            { VSGameConfig.SERVER.minScaling }
        )
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = ShipAssemblerItem(Properties().tab(CreativeModeTab.TAB_MISC))
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = ShipCreatorItem(
            Properties().tab(CreativeModeTab.TAB_MISC),
            { VSGameConfig.SERVER.miniShipSize },
            { VSGameConfig.SERVER.minScaling }
        )

        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = EntityType.Builder.of(
            ::ShipMountingEntity,
            MobCategory.MISC
        ).sized(.3f, .3f)
            .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())
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
        VSEntityManager.registerContraptionHandler(ContraptionShipyardEntityHandlerFabric)

        registerBlockAndItem("test_chair", ValkyrienSkiesMod.TEST_CHAIR)
        registerBlockAndItem("test_hinge", ValkyrienSkiesMod.TEST_HINGE)
        registerBlockAndItem("test_flap", ValkyrienSkiesMod.TEST_FLAP)
        registerBlockAndItem("test_wing", ValkyrienSkiesMod.TEST_WING)
        Registry.register(
            Registry.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_assembler"),
            ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM
        )
        Registry.register(
            Registry.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_creator"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM
        )
        Registry.register(
            Registry.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_creator_smaller"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER
        )
        Registry.register(
            Registry.ENTITY_TYPE, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity"),
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        )
        Registry.register(
            Registry.BLOCK_ENTITY_TYPE, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "test_hinge_block_entity"),
            ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE
        )

        CommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            VSCommands.registerServerCommands(dispatcher)
        }

        // registering data loaders
        val loader1 = MassDatapackResolver.loader // the get makes a new instance so get it only once
        val loader2 = VSEntityHandlerDataLoader // the get makes a new instance so get it only once
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

        VSKeyBindings.clientSetup {
            KeyBindingHelper.registerKeyBinding(it)
        }
    }

    private fun registerBlockAndItem(registryName: String, block: Block) {
        Registry.register(
            Registry.BLOCK, ResourceLocation(ValkyrienSkiesMod.MOD_ID, registryName),
            block
        )
        Registry.register(
            Registry.ITEM, ResourceLocation(ValkyrienSkiesMod.MOD_ID, registryName),
            BlockItem(block, Properties().tab(CreativeModeTab.TAB_MISC))
        )
    }
}
