package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.mod.common.ValkyrienSkiesMod

/**
 * Records VS traffic at the platform transport boundary (the bytes handed to / received from the
 * Fabric/Forge custom payload channel) into core's diagnostic counters under `wire/...` keys.
 *
 * Minecraft adds per-packet framing on top of these bytes (packet id varint + channel id string +
 * length varints, before compression), so the true on-wire size is slightly larger; see
 * [MC_CUSTOM_PAYLOAD_OVERHEAD_ESTIMATE].
 */
object VsNetworkStatistics {
    /** packet id (1) + "valkyrienskies:vs_packet" as varint-length string (1 + 24) + frame length varint (~1). */
    const val MC_CUSTOM_PAYLOAD_OVERHEAD_ESTIMATE = 27

    @JvmStatic
    fun record(direction: String, flow: String, channel: String, bytes: Int) {
        ValkyrienSkiesMod.vsCore.networkStatistics.record("wire/$direction/$flow/$channel", bytes)
    }
}
