package org.valkyrienskies.mod.forge.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.fml.network.NetworkRegistry
import net.minecraftforge.fml.network.PacketDistributor
import net.minecraftforge.fml.network.simple.SimpleChannel
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.mixinducks.server.IPlayerProvider

object VSForgeNetworking {

    private const val protocolVersion = "1"
    private val vsForgeChannel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet"),
        { protocolVersion },
        protocolVersion::equals,
        protocolVersion::equals
    )

    internal fun registerForgeNetworking() {
        registerClientPacketHandlers()
        injectForgePacketSenders()
    }

    private fun registerClientPacketHandlers() {
        // This gibberish is brought to you by forge
        // seriously forge wtf
        @Suppress("INACCESSIBLE_TYPE")
        vsForgeChannel.registerMessage(
            0,
            MessageVSPacket::class.java,
            { msg, buf -> buf.writeBytes(msg.buf) },
            { packetBuffer: FriendlyByteBuf -> MessageVSPacket(packetBuffer) },
            { vsPacket, ctx ->
                val sender = ctx.get().sender
                if (sender != null) {
                    val vsSender = (sender.server as IPlayerProvider).getPlayer(sender.uuid)
                    VSNetworking.TCP.onReceiveServer(vsPacket.buf, vsSender)
                } else {
                    VSNetworking.TCP.onReceiveClient(vsPacket.buf)
                }
                ctx.get().packetHandled = true
            }
        )
    }

    private fun injectForgePacketSenders() {
        VSNetworking.TCP.rawSendToClient = { data, player ->
            vsForgeChannel.send(
                PacketDistributor.PLAYER.with { player.mcPlayer as ServerPlayer },
                MessageVSPacket(data)
            )
        }
        VSNetworking.TCP.rawSendToServer = { data ->
            vsForgeChannel.send(PacketDistributor.SERVER.noArg(), MessageVSPacket(data))
        }
    }
}
