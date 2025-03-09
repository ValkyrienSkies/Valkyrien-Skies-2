package org.valkyrienskies.mod.fabric.common

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.apigame.hooks.CoreHooksIn
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.fabric.common.VSPacket.Companion.VS_PACKET_CODEC
import org.valkyrienskies.mod.fabric.common.VSPacket.Companion.VS_PACKET_TYPE

/**
 * Registers VS with the Fabric networking API.
 */
class VSFabricNetworking(
    private val isClient: Boolean
) {
    private fun registerClientNetworking(hooks: CoreHooksIn) {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_TYPE) { packet, context ->
            hooks.onReceiveClient(Unpooled.wrappedBuffer(packet.data))
        }
    }

    fun register(hooks: CoreHooksIn) {
        if (isClient) {
            registerClientNetworking(hooks)
        }

        ServerPlayNetworking.registerGlobalReceiver(VS_PACKET_TYPE) { packet, context ->
            hooks.onReceiveServer(Unpooled.wrappedBuffer(packet.data), context.player().playerWrapper)
        }

        PayloadTypeRegistry.configurationC2S()
            .register(VS_PACKET_TYPE, VS_PACKET_CODEC)
    }

    fun sendToClient(data: ByteBuf, player: IPlayer) {
        val serverPlayer = (player as MinecraftPlayer).player as ServerPlayer
        ServerPlayNetworking.send(serverPlayer, VSPacket(data.copyToByteArray()))
    }

    fun sendToServer(data: ByteBuf) {
        ClientPlayNetworking.send(VSPacket(data.copyToByteArray()))
    }

    private fun ByteBuf.copyToByteArray(): ByteArray {
        val byteArray = ByteArray(readableBytes())
        getBytes(readerIndex(), byteArray)
        return byteArray
    }
}

class VSPacket(internal val data: ByteArray) : CustomPacketPayload {
    override fun type() = VS_PACKET_TYPE

    companion object {
        val VS_PACKET_RL: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "vs_packet")
        val VS_PACKET_TYPE = CustomPacketPayload.Type<VSPacket>(VS_PACKET_RL)
        val VS_PACKET_CODEC: StreamCodec<FriendlyByteBuf, VSPacket> = StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, VSPacket::data) {
            VSPacket(it)
        }
    }
}
