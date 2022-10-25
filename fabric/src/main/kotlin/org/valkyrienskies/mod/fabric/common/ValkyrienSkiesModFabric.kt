package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
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
import org.valkyrienskies.core.hooks.CoreHooks
import org.valkyrienskies.core.program.DaggerVSCoreClientFactory
import org.valkyrienskies.core.program.DaggerVSCoreServerFactory
import org.valkyrienskies.core.program.VSCoreModule
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSEntityHandlerDataLoader
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.item.ShipAssemblerItem
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class ValkyrienSkiesModFabric : ModInitializer {

    companion object {
        init {
            CoreHooks = FabricHooksImpl
        }
    }

    override fun onInitialize() {
        ValkyrienSkiesMod.TEST_CHAIR = TestChairBlock
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC), 1.0)
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = ShipAssemblerItem(Properties().tab(CreativeModeTab.TAB_MISC))
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC), 0.5)
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = EntityType.Builder.of(
            ::ShipMountingEntity,
            MobCategory.MISC
        ).sized(.3f, .3f)
            .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())

        val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

        val module = VSCoreModule(FabricHooksImpl, VSFabricNetworking(isClient))

        val vsCore = if (isClient) {
            DaggerVSCoreClientFactory.builder().vSCoreModule(module).build().client()
        } else {
            DaggerVSCoreServerFactory.builder().vSCoreModule(module).build().server()
        }

        if (isClient) onInitializeClient()

        ValkyrienSkiesMod.init(vsCore)

        registerBlockAndItem("test_chair", ValkyrienSkiesMod.TEST_CHAIR)
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
    }

    /**
     * Only run on client
     */
    private fun onInitializeClient() {
        // Register the ship mounting entity renderer
        EntityRendererRegistry.INSTANCE.register(
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        ) { manager: EntityRenderDispatcher, _: EntityRendererRegistry.Context ->
            EmptyRenderer(
                manager
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

    fun addonInit(name: String) {
        // do nothing, causes static init to activate
        // TODO make this api'ish
    }
}
