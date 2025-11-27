package org.valkyrienskies.mod.platform

import com.mojang.logging.LogUtils
import net.minecraftforge.fml.loading.FMLEnvironment
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.VsiCoreFactory
import org.valkyrienskies.mod.common.VSCoreProvider
import org.valkyrienskies.mod.forge.common.ForgeHooksImpl
import org.valkyrienskies.mod.forge.common.VSForgeNetworking

class VSCoreProviderImpl: VSCoreProvider {
    override fun newVSCore(): VsiCore {
        val core = if (FMLEnvironment.dist.isClient) {
            VsiCoreFactory.instance.newVsCoreClient(ForgeHooksImpl)
        } else {
            VsiCoreFactory.instance.newVsCoreServer(ForgeHooksImpl)
        }
        VSForgeNetworking.registerPacketHandlers(core.hooks)
        LogUtils.getLogger().info("Initialized VSCore on Forge!")
        return core
    }
}
