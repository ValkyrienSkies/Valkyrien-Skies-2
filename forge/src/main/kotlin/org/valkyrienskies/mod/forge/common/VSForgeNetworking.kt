package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerLoginPacketListenerImpl
import net.minecraftforge.network.HandshakeHandler
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.SimpleChannel
import org.apache.commons.lang3.tuple.Pair
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksIn
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.networking.VsNetworkPlatform
import org.valkyrienskies.mod.common.networking.VsNetworkStatistics
import org.valkyrienskies.mod.common.playerWrapper
import java.util.function.IntSupplier

object VSForgeNetworking {

    // Protocol version for session-framed play messages. Forge rejects peers with a different channel version at login, with its own message.
    private val protocolVersion = "2"
    private val vsForgeChannel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet"),
        { protocolVersion },
        protocolVersion::equals,
        protocolVersion::equals
    )

    /** Login-phase VS handshake messages (FML login packets carry a login index). */
    class LoginHello(val bytes: ByteArray) : IntSupplier {
        var loginIndex = 0
        override fun getAsInt() = loginIndex
    }

    class LoginReply(val bytes: ByteArray) : IntSupplier {
        var loginIndex = 0
        override fun getAsInt() = loginIndex
    }

    fun registerPacketHandlers(hooks: VsiCoreHooksIn) {
        // VS session payloads — message ID 0 (fragmentation is handled by the VS session)
        @Suppress("INACCESSIBLE_TYPE")
        vsForgeChannel.registerMessage(
            0,
            MessageVSPacket::class.java,
            { msg, buf -> buf.writeBytes(msg.buf) },
            { packetBuffer: FriendlyByteBuf -> MessageVSPacket(packetBuffer) },
            { vsPacket, ctx ->
                val sender = ctx.get().sender
                VsNetworkStatistics.record("recv", if (sender != null) "c2s" else "s2c", "vs_packet", vsPacket.buf.readableBytes())
                if (sender != null) {
                    hooks.onReceiveServer(vsPacket.buf, sender.playerWrapper)
                } else {
                    hooks.onReceiveClient(vsPacket.buf)
                }
                ctx.get().packetHandled = true
            }
        )

        // Server → client handshake hello (one per login).
        vsForgeChannel.messageBuilder(LoginHello::class.java, 2, NetworkDirection.LOGIN_TO_CLIENT)
            .loginIndex({ it.loginIndex }, { msg, index -> msg.loginIndex = index })
            .encoder { msg, buf -> buf.writeByteArray(msg.bytes) }
            .decoder { buf -> LoginHello(buf.readByteArray()) }
            .buildLoginPacketList { _ ->
                listOf(Pair.of("Valkyrien Skies network handshake", LoginHello(VsNetworkPlatform.startLogin().hello)))
            }
            .consumerNetworkThread(HandshakeHandler.biConsumerFor<LoginHello> { _, msg, ctx ->
                val reply = ValkyrienSkiesMod.vsCore.networkHost.clientHandleLoginHello(Unpooled.wrappedBuffer(msg.bytes))
                vsForgeChannel.reply(LoginReply(reply), ctx.get())
                ctx.get().packetHandled = true
            })
            .add()

        // Client → server handshake reply.
        vsForgeChannel.messageBuilder(LoginReply::class.java, 3, NetworkDirection.LOGIN_TO_SERVER)
            .loginIndex({ it.loginIndex }, { msg, index -> msg.loginIndex = index })
            .encoder { msg, buf -> buf.writeByteArray(msg.bytes) }
            .decoder { buf -> LoginReply(buf.readByteArray()) }
            .consumerNetworkThread(HandshakeHandler.indexFirst<LoginReply> { _, msg, ctx ->
                val connection = ctx.get().networkManager
                // The hello the client answered was built from this same server registry, so a fresh handshake
                // object evaluates the reply identically.
                VsNetworkPlatform.finishLogin(connection, VsNetworkPlatform.startLogin(), Unpooled.wrappedBuffer(msg.bytes))
                    ?.let { reason -> connection.disconnect(Component.literal(reason)) }
                ctx.get().packetHandled = true
            })
            .add()
    }

    fun sendToClient(data: ByteBuf, player: VsiPlayer) {
        // Mock players don't have an MC player reference.
        if (player !is org.valkyrienskies.mod.common.util.MinecraftPlayer) return
        val target = PacketDistributor.PLAYER.with { player.mcPlayer as ServerPlayer }
        VsNetworkStatistics.record("send", "s2c", "vs_packet", data.readableBytes())
        vsForgeChannel.send(target, MessageVSPacket(data))
    }

    fun sendToServer(data: ByteBuf) {
        VsNetworkStatistics.record("send", "c2s", "vs_packet", data.readableBytes())
        vsForgeChannel.send(PacketDistributor.SERVER.noArg(), MessageVSPacket(data))
    }
}
