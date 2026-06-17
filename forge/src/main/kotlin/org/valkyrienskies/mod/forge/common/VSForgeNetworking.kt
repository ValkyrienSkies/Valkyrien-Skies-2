package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.SimpleChannel
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksIn
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.networking.VSPacketFragmenter
import org.valkyrienskies.mod.common.playerWrapper

object VSForgeNetworking {

    private val protocolVersion = "1"
    private val vsForgeChannel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet"),
        { protocolVersion },
        protocolVersion::equals,
        protocolVersion::equals
    )

    fun registerPacketHandlers(hooks: VsiCoreHooksIn) {
        // Regular (non-fragmented) packets — message ID 0
        @Suppress("INACCESSIBLE_TYPE")
        vsForgeChannel.registerMessage(
            0,
            MessageVSPacket::class.java,
            { msg, buf -> buf.writeBytes(msg.buf) },
            { packetBuffer: FriendlyByteBuf -> MessageVSPacket(packetBuffer) },
            { vsPacket, ctx ->
                val sender = ctx.get().sender
                if (sender != null) {
                    hooks.onReceiveServer(vsPacket.buf, sender.playerWrapper)
                } else {
                    hooks.onReceiveClient(vsPacket.buf)
                }
                ctx.get().packetHandled = true
            }
        )

        // Fragment packets — message ID 1
        @Suppress("INACCESSIBLE_TYPE")
        vsForgeChannel.registerMessage(
            1,
            MessageVSPacketFragment::class.java,
            { msg, buf -> buf.writeBytes(msg.buf) },
            { packetBuffer: FriendlyByteBuf -> MessageVSPacketFragment(packetBuffer) },
            { vsPacket, ctx ->
                val assembled = VSPacketFragmenter.onReceiveFragment(vsPacket.buf)
                if (assembled != null) {
                    val sender = ctx.get().sender
                    if (sender != null) {
                        hooks.onReceiveServer(assembled, sender.playerWrapper)
                    } else {
                        hooks.onReceiveClient(assembled)
                    }
                }
                ctx.get().packetHandled = true
            }
        )
    }

    fun sendToClient(data: ByteBuf, player: VsiPlayer) {
        // Mock players don't have an MC player reference.
        if (player !is org.valkyrienskies.mod.common.util.MinecraftPlayer) return
        val target = PacketDistributor.PLAYER.with { player.mcPlayer as ServerPlayer }

        if (VSPacketFragmenter.needsSplitting(data)) {
            for (fragment in VSPacketFragmenter.split(data)) {
                vsForgeChannel.send(target, MessageVSPacketFragment(fragment))
            }
        } else {
            vsForgeChannel.send(target, MessageVSPacket(data))
        }
    }

    fun sendToServer(data: ByteBuf) {
        vsForgeChannel.send(PacketDistributor.SERVER.noArg(), MessageVSPacket(data))
    }
}
