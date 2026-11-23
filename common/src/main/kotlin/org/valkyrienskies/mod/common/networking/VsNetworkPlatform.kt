package org.valkyrienskies.mod.common.networking

import io.netty.buffer.ByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.valkyrienskies.core.internal.network.NetworkTransport
import org.valkyrienskies.core.internal.network.ServerLoginHandshake
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.network.ConnectionAccessor
import org.valkyrienskies.mod.mixin.accessors.server.network.ServerGamePacketListenerImplAccessor
import net.minecraft.network.Connection

/**
 * Loader-independent glue between Minecraft connections and VS networking (server side and shared constants).
 * Client-only parts live in [org.valkyrienskies.mod.client.network.VsNetworkClient].
 */
object VsNetworkPlatform {
    /** Under Minecraft's 1 MiB clientbound custom payload limit, leaving room for framing. */
    const val MAX_TO_CLIENT = 1_000_000

    /** Under Minecraft's 32,767 byte serverbound custom payload limit. */
    const val MAX_TO_SERVER = 32_000

    /** Login query / login packet channel for the VS handshake. */
    @JvmField
    val HANDSHAKE_ID = ResourceLocation(ValkyrienSkiesMod.MOD_ID, "handshake")

    // Accepted login handshakes until the player joins, keyed by connection (the same object from login to play).
    // Not by profile id: on offline-mode servers the profile only gets its UUID after login negotiation.
    private val acceptedLogins = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Connection, ServerLoginHandshake>())

    private val host get() = vsCore.networkHost

    // Wrappers of the players attached on this server: Minecraft doesn't call onDisconnect for players still online
    // when it stops, and a session left open keeps the datagram lane's (non-daemon) threads running.
    private val attachedServer = java.util.concurrent.ConcurrentHashMap.newKeySet<MinecraftPlayer>()

    @JvmStatic
    fun startLogin(): ServerLoginHandshake = host.serverLoginStart()

    /** Evaluates the client's handshake reply. Returns a disconnect reason, or null if compatible. */
    @JvmStatic
    fun finishLogin(connection: Connection, handshake: ServerLoginHandshake, reply: ByteBuf): String? {
        val reason = handshake.handleReply(reply)
        if (reason == null) acceptedLogins[connection] = handshake
        return reason
    }

    @JvmStatic
    fun attachServer(player: ServerPlayer) {
        val login = acceptedLogins.remove(player.connection.minecraftConnection)
        LOGGER.info("Attaching VS network session for {} ({} handshake)", player.gameProfile.name,
            if (login != null) "login" else "play-phase")
        attachedServer.add(player.playerWrapper)
        host.serverAttach(player.playerWrapper, login, ServerTransport(player.connection), player.server)
    }

    private val LOGGER = org.apache.logging.log4j.LogManager.getLogger("VS Net")

    @JvmStatic
    fun detachServer(player: ServerPlayer) {
        attachedServer.remove(player.playerWrapper)
        host.serverDetach(player.playerWrapper)
    }

    /** Closes the sessions of the players still attached (the server is stopping). */
    @JvmStatic
    fun detachAllServer() {
        for (player in attachedServer.toList()) {
            attachedServer.remove(player)
            host.serverDetach(player)
        }
    }

    @JvmStatic
    fun flushServer(player: ServerPlayer) = host.flush(player.playerWrapper)

    @JvmStatic
    fun flushAllServer() = host.flushAllServer()

    /** Client-side flush that doesn't reference client classes (usable from common mixins). */
    @JvmStatic
    fun flushClient() = host.flush(null)

    // ServerGamePacketListenerImpl.connection is private on Fabric (Forge's access transformer widens it).
    private val ServerGamePacketListenerImpl.minecraftConnection: Connection
        get() = (this as ServerGamePacketListenerImplAccessor).`valkyrienskies$getConnection`()

    /** Bound to the connection's listener, which outlives the player entity (respawn replaces [ServerGamePacketListenerImpl.player]). */
    private class ServerTransport(private val listener: ServerGamePacketListenerImpl) : NetworkTransport {
        private val connection = listener.minecraftConnection
        private val server = listener.player.server

        override val maxPayloadSize: Int get() = MAX_TO_CLIENT

        override fun send(data: ByteBuf) = vsCore.hooks.sendToClient(data, listener.player.playerWrapper)

        override fun isWritable(): Boolean =
            (connection as ConnectionAccessor).`valkyrienskies$getChannel`()?.isWritable ?: true

        override fun disconnect(reason: String) {
            server.execute { listener.disconnect(Component.literal(reason)) }
        }

        override fun isOwnerThread(): Boolean = server.isSameThread
    }
}
