package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf

/**
 * A wrapper of [IVSPacket] used to register forge networking.
 */
class MessageVSPacket(val buf: ByteBuf)
