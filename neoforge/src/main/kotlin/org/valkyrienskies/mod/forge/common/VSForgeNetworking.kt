package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.registration.NetworkRegistry
import org.valkyrienskies.core.apigame.hooks.CoreHooksIn
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.playerWrapper

object VSForgeNetworking {

    private val protocolVersion = "1"
    private val vsForgeChannel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "vs_packet"),
        { protocolVersion },
        protocolVersion::equals,
        protocolVersion::equals
    )

    fun registerPacketHandlers(hooks: CoreHooksIn) {
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
                    hooks.onReceiveServer(vsPacket.buf, vsSender)
                } else {
                    hooks.onReceiveClient(vsPacket.buf)
                }
                ctx.get().packetHandled = true
            }
        )
    }

    fun sendToClient(data: ByteBuf, player: IPlayer) {
        vsForgeChannel.send(
            PacketDistributor.PLAYER.with { player.mcPlayer as ServerPlayer },
            MessageVSPacket(data)
        )
    }

    fun sendToServer(data: ByteBuf) {
        vsForgeChannel.send(PacketDistributor.SERVER.noArg(), MessageVSPacket(data))
    }
}
