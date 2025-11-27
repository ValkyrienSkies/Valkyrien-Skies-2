package org.valkyrienskies.mod.platform

import com.mojang.logging.LogUtils
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.VsiCoreFactory
import org.valkyrienskies.mod.common.VSCoreProvider
import org.valkyrienskies.mod.fabric.common.FabricHooksImpl
import org.valkyrienskies.mod.fabric.common.VSFabricNetworking

class VSCoreProviderImpl: VSCoreProvider {
    override fun newVSCore(): VsiCore {
        val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT
        val networking = VSFabricNetworking(isClient)
        val hooks = FabricHooksImpl(networking)

        val vsCore = if (isClient) {
            VsiCoreFactory.instance.newVsCoreClient(hooks)
        } else {
            VsiCoreFactory.instance.newVsCoreServer(hooks)
        }
        networking.register(vsCore.hooks)
        LogUtils.getLogger().info("Initialized VSCore on Fabric!")
        return vsCore
    }
}
