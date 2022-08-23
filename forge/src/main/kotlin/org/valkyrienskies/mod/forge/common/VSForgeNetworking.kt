package org.valkyrienskies.mod.forge.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.fml.network.NetworkRegistry
import net.minecraftforge.fml.network.PacketDistributor
import net.minecraftforge.fml.network.simple.SimpleChannel
import org.valkyrienskies.core.networking.NetworkChannel
import org.valkyrienskies.core.networking.VSNetworkingConfigurator
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.playerWrapper

class VSForgeNetworking : VSNetworkingConfigurator {

    private val protocolVersion = "1"
    private val vsForgeChannel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet"),
        { protocolVersion },
        protocolVersion::equals,
        protocolVersion::equals
    )

    private fun registerPacketHandlers(channel: NetworkChannel) {
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
                    val vsSender = sender.playerWrapper
                    channel.onReceiveServer(vsPacket.buf, vsSender)
                } else {
                    channel.onReceiveClient(vsPacket.buf)
                }
                ctx.get().packetHandled = true
            }
        )
    }

    private fun injectForgePacketSenders(channel: NetworkChannel) {
        channel.rawSendToClient = { data, player ->
            vsForgeChannel.send(
                PacketDistributor.PLAYER.with { player.mcPlayer as ServerPlayer },
                MessageVSPacket(data)
            )
        }
        channel.rawSendToServer = { data ->
            vsForgeChannel.send(PacketDistributor.SERVER.noArg(), MessageVSPacket(data))
        }
    }

    override fun configure(channel: NetworkChannel) {
        registerPacketHandlers(channel)
        injectForgePacketSenders(channel)
    }
}
