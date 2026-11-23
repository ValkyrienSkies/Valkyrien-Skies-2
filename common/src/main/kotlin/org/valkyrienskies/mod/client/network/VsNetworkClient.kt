package org.valkyrienskies.mod.client.network

import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.internal.network.NetworkTransport
import org.valkyrienskies.mod.common.networking.VsNetworkPlatform
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.network.ConnectionAccessor

/** Client side of the VS networking platform glue (client-only: references [Minecraft]). */
object VsNetworkClient {
    @JvmStatic
    fun attach(minecraft: Minecraft) = vsCore.networkHost.clientAttach(ClientTransport(minecraft), minecraft)

    @JvmStatic
    fun detach() {
        if (LOGGER.isDebugEnabled) {
            LOGGER.debug("VS network session closing; sessions: {}", vsCore.networkHost.describeSessions())
            vsCore.networkStatistics.snapshot().sortedByDescending { it.bytes }.take(25).forEach {
                LOGGER.debug("  {} {} pkts {} B", it.key, it.packets, it.bytes)
            }
        }
        vsCore.networkHost.clientDetach()
    }

    private val LOGGER = org.apache.logging.log4j.LogManager.getLogger("VS Net")

    @JvmStatic
    fun flush() = vsCore.networkHost.flush(null)

    private class ClientTransport(private val minecraft: Minecraft) : NetworkTransport {
        override val maxPayloadSize: Int get() = VsNetworkPlatform.MAX_TO_SERVER

        override fun send(data: ByteBuf) {
            // Send on the client main thread: Forge's channel send from inside a netty read (e.g. replying to a
            // control message) has been observed to drop the connection. Minecraft.execute is FIFO, so order is kept.
            if (minecraft.isSameThread) vsCore.hooks.sendToServer(data)
            else minecraft.execute { vsCore.hooks.sendToServer(data) }
        }

        override fun isWritable(): Boolean =
            (minecraft.connection?.connection as? ConnectionAccessor)?.`valkyrienskies$getChannel`()?.isWritable ?: true

        override fun disconnect(reason: String) {
            minecraft.execute { minecraft.connection?.connection?.disconnect(Component.literal(reason)) }
        }

        override fun isOwnerThread(): Boolean = minecraft.isSameThread
    }
}
