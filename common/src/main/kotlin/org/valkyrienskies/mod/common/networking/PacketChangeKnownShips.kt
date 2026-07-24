package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

/**
 * Tells the server which ships the player now "knows about" on the client
 * (for collision-safety gating — server skips collisions with ships the
 * player can't see yet). Carries a list so that bulk ship spawns don't
 * trigger one roundtrip packet per ship — sending 1000 individual packets
 * during a /vs perf-test was costing ~100ms of client-settle time on its own.
 */
data class PacketChangeKnownShips(
    val add: Boolean,
    val shipIDs: LongArray,
): SimplePacket {
    // LongArray requires custom equals/hashCode to behave like a data class.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketChangeKnownShips) return false
        return add == other.add && shipIDs.contentEquals(other.shipIDs)
    }

    override fun hashCode(): Int = 31 * add.hashCode() + shipIDs.contentHashCode()
}
