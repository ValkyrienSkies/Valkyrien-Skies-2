package net.examplemod.networking

import io.netty.buffer.ByteBuf

/**
 * Used to send data between client and server
 */
interface IVSPacket {
    fun write(byteBuf: ByteBuf)
    fun read(byteBuf: ByteBuf)
}