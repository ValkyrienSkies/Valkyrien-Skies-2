package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf

/**
 * Wrapper for fragmented VS2 packets that exceed the 1 MB vanilla payload limit.
 * Registered on a separate message ID from [MessageVSPacket] so Forge can
 * distinguish regular packets from fragments.
 */
class MessageVSPacketFragment(val buf: ByteBuf)
