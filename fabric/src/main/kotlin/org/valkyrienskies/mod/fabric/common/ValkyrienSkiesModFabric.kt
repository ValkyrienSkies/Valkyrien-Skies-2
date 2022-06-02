package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceReloadListener.Synchronizer
import net.minecraft.resource.ResourceType.SERVER_DATA
import net.minecraft.util.Identifier
import net.minecraft.util.profiler.Profiler
import net.minecraft.util.registry.Registry
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class ValkyrienSkiesModFabric : ModInitializer {
    override fun onInitialize() {
        ValkyrienSkiesMod.init()
        Registry.register(
            Registry.ITEM, Identifier(ValkyrienSkiesMod.MOD_ID, "ship_creator"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM
        )
        Registry.register(
            Registry.ITEM, Identifier(ValkyrienSkiesMod.MOD_ID, "ship_creator_smaller"),
            ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER
        )
        VSFabricNetworking.injectFabricPacketSenders()

        //registering mass
        val loader = ValkyrienSkiesMod.MASS_DATAPACK_RESOLVER.loader //the get makes a new instance so get it only once
        ResourceManagerHelper.get(SERVER_DATA)
            .registerReloadListener(object : IdentifiableResourceReloadListener {
                override fun getFabricId(): Identifier {
                    return Identifier(ValkyrienSkiesMod.MOD_ID, "vs_mass")
                }

                override fun reload(
                    synchronizer: Synchronizer,
                    resourceManager: ResourceManager,
                    profiler: Profiler, profiler2: Profiler,
                    executor: Executor, executor2: Executor
                ): CompletableFuture<Void> {
                    return loader.reload(
                        synchronizer, resourceManager, profiler, profiler2,
                        executor, executor2
                    )
                }
            })
    }
}
