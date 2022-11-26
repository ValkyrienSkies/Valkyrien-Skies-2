package org.valkyrienskies.mod.fabric.common

import io.netty.buffer.ByteBuf
import net.fabricmc.api.EnvType.CLIENT
import net.fabricmc.loader.api.FabricLoader
import org.valkyrienskies.core.api.world.IPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.CommonHooksImpl
import java.nio.file.Path

class FabricHooksImpl(private val networking: VSFabricNetworking) : CommonHooksImpl() {

    override val isPhysicalClient: Boolean
        get() = FabricLoader.getInstance().environmentType == CLIENT

    override val configDir: Path
        get() = FabricLoader.getInstance().configDir.resolve(ValkyrienSkiesMod.MOD_ID)

    override fun sendToServer(buf: ByteBuf) {
        networking.sendToServer(buf)
    }

    override fun sendToClient(buf: ByteBuf, player: IPlayer) {
        networking.sendToClient(buf, player)
    }
}
