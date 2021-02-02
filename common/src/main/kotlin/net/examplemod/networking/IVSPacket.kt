package net.examplemod.networking

import io.netty.buffer.ByteBuf

interface IVSPacket {
    fun write(byteBuf: ByteBuf)
    fun read(byteBuf: ByteBuf)
}