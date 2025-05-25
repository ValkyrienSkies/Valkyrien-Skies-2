package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

/**
 * A wrapper of [IVSPacket] used to register forge networking.
 */
class MessageVSPacket(val buf: ByteBuf): CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MessageVSPacket> = CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(MOD_ID, "vs_packet"))

        val STREAM_CODEC = object: StreamCodec<ByteBuf, MessageVSPacket> {
            override fun decode(
                byteBuf: ByteBuf
            ) = MessageVSPacket(byteBuf.copy())

            override fun encode(
                byteBuf: ByteBuf, messageVSPacket: MessageVSPacket
            ) {
                byteBuf.writeBytes(messageVSPacket.buf)
            }
        }
    }
}
