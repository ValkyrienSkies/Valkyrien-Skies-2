package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.HandlerThread
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import org.valkyrienskies.core.apigame.hooks.CoreHooksIn
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck
import java.util.concurrent.CompletableFuture

object VSForgeNetworking {
    lateinit var registrar: PayloadRegistrar
    lateinit var hooks: CoreHooksIn

    fun registerPacketHandlers(hooks: CoreHooksIn) {
        // Loading this class registers the handlers
        this.hooks = hooks
    }

    fun sendToClient(data: ByteBuf, player: IPlayer) {
        PacketDistributor.sendToPlayer(player.mcPlayer as ServerPlayer, VSPacket(data.copyToByteArray()))
    }

    fun sendToServer(data: ByteBuf) {
        PacketDistributor.sendToServer(VSPacket(data.copyToByteArray()))
    }

    @SubscribeEvent
    fun register(event: RegisterPayloadHandlersEvent) {
        // Sets the current network version
        registrar = event.registrar("1")
        registrar = registrar.executesOn(HandlerThread.NETWORK)
        registrar = registrar.playBidirectional(
            VSPacket.VS_PACKET_TYPE,
            VSPacket.VS_PACKET_CODEC,
            DirectionalPayloadHandler<VSPacket>(
                ::handleClient,
                ::handleServer,
            )
        )
    }

    fun handleClient(
        message: VSPacket,
        context: IPayloadContext,
    ): CompletableFuture<Void?> = context.enqueueWork {
        hooks.onReceiveClient(Unpooled.wrappedBuffer(message.data))
    }

    fun handleServer(
        message: VSPacket,
        context: IPayloadContext,
    ): CompletableFuture<Void?> = context.enqueueWork {
        val player = context.player()
        if (player != null) {
            hooks.onReceiveServer(Unpooled.wrappedBuffer(message.data), (player as PlayerDuck).vs_getPlayer())
        } else {
            println("context.sender was null?")
        }
    }

    private fun ByteBuf.copyToByteArray(): ByteArray {
        val byteArray = ByteArray(readableBytes())
        getBytes(readerIndex(), byteArray)
        return byteArray
    }
}
