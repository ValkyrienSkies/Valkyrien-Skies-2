package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraftforge.fml.LogicalSide.CLIENT
import net.minecraftforge.fml.network.NetworkRegistry
import net.minecraftforge.fml.network.PacketDistributor
import net.minecraftforge.fml.network.simple.SimpleChannel
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer

object VSForgeNetworking {

    private const val protocolVersion = "1"

    private val vsForgeChannel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        Identifier(ValkyrienSkiesMod.MOD_ID, "vs_packet"),
        { protocolVersion },
        { version: String? -> protocolVersion == version },
        { version: String? -> protocolVersion == version }
    )

    private class VSForgeMessage(val byteBuf: ByteBuf)

    internal fun registerForgeNetworking() {
        // This gibberish is brought to you by forge
        @Suppress("INACCESSIBLE_TYPE")
        vsForgeChannel.registerMessage(
            // Who knows lol
            0,
            VSForgeMessage::class.java,
            // Convert VSForgeMessage -> ByteBuf
            { msg, buf -> buf.writeBytes(msg.byteBuf) },
            // Convert ByteBuf -> VSForgeMessage
            { buf -> VSForgeMessage(buf.copy()) },
            // Handle packets
            { msg, ctxSupplier ->
                val ctx = ctxSupplier.get()
                if (ctx.direction.receptionSide == CLIENT) {
                    VSNetworking.TCP.onReceiveClient(msg.byteBuf)
                } else {
                    VSNetworking.TCP.onReceiveServer(msg.byteBuf, MinecraftPlayer.wrap(ctx.sender!!))
                }
            }
        )

        VSNetworking.TCP.rawSendToClient = { data, player ->
            vsForgeChannel.send(
                PacketDistributor.PLAYER.with { player.mcPlayer as ServerPlayerEntity },
                VSForgeMessage(data)
            )
        }
        VSNetworking.TCP.rawSendToServer = { data ->
            vsForgeChannel.sendToServer(VSForgeMessage(data))
        }
    }
}
