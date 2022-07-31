package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
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
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class ValkyrienSkiesModFabric : ModInitializer {
    override fun onInitialize() {
        CoreHooks = FabricHooksImpl

        ValkyrienSkiesMod.TEST_CHAIR = TestChairBlock
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC), 1.0)
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = ShipCreatorItem(Properties().tab(CreativeModeTab.TAB_MISC), 0.5)
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = EntityType.Builder.of(
            ::ShipMountingEntity,
            MobCategory.MISC
        ).sized(.3f, .3f)
            .build(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_mounting_entity").toString())

        ValkyrienSkiesMod.init()

        registerBlockAndItem("test_chair", ValkyrienSkiesMod.TEST_CHAIR)
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
        VSFabricNetworking.injectFabricPacketSenders()
        VSFabricNetworking.registerServerPacketHandlers()

        // registering mass
        val loader = MassDatapackResolver.loader // the get makes a new instance so get it only once
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
                    return loader.reload(
                        stage, resourceManager, preparationsProfiler, reloadProfiler,
                        backgroundExecutor, gameExecutor
                    )
                }
            })
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
