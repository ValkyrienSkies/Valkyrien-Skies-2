package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.networking.simple.SimplePacket

/**
 * A packet that is sent to the client to configure the entity handlers,
 *  which are stored in data packs on the server
 */
data class PacketSyncVSEntityTypes(
    // Mc entity type id -> VSEntityHandler name
    val entity2Handler: Array<String>
) : SimplePacket
