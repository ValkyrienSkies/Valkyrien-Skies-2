package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.CommonHooksImpl
import java.nio.file.Path

object ForgeHooksImpl : CommonHooksImpl() {

    override val isPhysicalClient: Boolean
        get() = FMLEnvironment.dist.isClient

    override val configDir: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(ValkyrienSkiesMod.MOD_ID)

    override fun sendToServer(buf: ByteBuf) {
        VSForgeNetworking.sendToServer(buf)
    }

    override fun sendToClient(buf: ByteBuf, player: IPlayer) {
        VSForgeNetworking.sendToClient(buf, player)
    }
}
