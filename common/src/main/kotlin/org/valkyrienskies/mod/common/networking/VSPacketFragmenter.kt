package org.valkyrienskies.mod.common.networking

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Splits large VS2 packets into fragments that fit within Minecraft's 1 MB payload limit,
 * and reassembles them on the receiving end.
 *
 * Fragment wire format:
 *   [groupId: int] [index: short] [totalCount: short] [payload: bytes]
 *
 * Fragments are sent on a separate channel/message ID from regular packets so
 * the normal packet path has zero overhead.
 */
object VSPacketFragmenter {

    // 900 KB per fragment — leaves headroom for Forge/Fabric framing under the 1 MB limit
    const val MAX_PAYLOAD_SIZE = 900_000
    private const val FRAGMENT_HEADER_SIZE = 8 // 4 (groupId) + 2 (index) + 2 (total)

    private val nextGroupId = AtomicInteger(0)

    // Client-side reassembly buffer: groupId -> array of fragment payloads
    private val pending = ConcurrentHashMap<Int, Array<ByteBuf?>>()

    /**
     * Returns true if [buf] is too large to send as a single packet.
     */
    @JvmStatic
    fun needsSplitting(buf: ByteBuf): Boolean = buf.readableBytes() > MAX_PAYLOAD_SIZE

    /**
     * Split a large [ByteBuf] into fragment buffers, each with a header.
     * The caller should send each returned buffer as a separate fragment packet.
     * Does NOT consume [buf] — reads from current readerIndex.
     */
    @JvmStatic
    fun split(buf: ByteBuf): List<ByteBuf> {
        val groupId = nextGroupId.getAndIncrement()
        val maxChunkSize = MAX_PAYLOAD_SIZE - FRAGMENT_HEADER_SIZE
        val totalBytes = buf.readableBytes()
        val totalFragments = (totalBytes + maxChunkSize - 1) / maxChunkSize

        val fragments = ArrayList<ByteBuf>(totalFragments)
        for (i in 0 until totalFragments) {
            val chunkSize = minOf(maxChunkSize, buf.readableBytes())
            val fragment = Unpooled.buffer(FRAGMENT_HEADER_SIZE + chunkSize)
            fragment.writeInt(groupId)
            fragment.writeShort(i)
            fragment.writeShort(totalFragments)
            fragment.writeBytes(buf, chunkSize)
            fragments.add(fragment)
        }
        return fragments
    }

    /**
     * Buffer a received fragment. Returns the fully reassembled [ByteBuf] when all
     * fragments of a group have arrived, or null if still waiting for more.
     */
    @JvmStatic
    fun onReceiveFragment(buf: ByteBuf): ByteBuf? {
        val groupId = buf.readInt()
        val index = buf.readUnsignedShort()
        val total = buf.readUnsignedShort()

        val fragments = pending.computeIfAbsent(groupId) { arrayOfNulls(total) }
        fragments[index] = Unpooled.copiedBuffer(buf)

        // Check if all fragments arrived
        if (fragments.all { it != null }) {
            pending.remove(groupId)
            @Suppress("UNCHECKED_CAST")
            return Unpooled.wrappedBuffer(*(fragments as Array<ByteBuf>))
        }
        return null
    }

    /**
     * Clear any buffered fragments (e.g., on disconnect).
     */
    @JvmStatic
    fun clear() {
        for (fragments in pending.values) {
            for (buf in fragments) {
                buf?.release()
            }
        }
        pending.clear()
    }
}
