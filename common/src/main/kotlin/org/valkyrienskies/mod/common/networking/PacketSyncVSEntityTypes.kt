package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.networking.simple.SimplePacket

data class PacketSyncVSEntityTypes(
    // Mc entity type id -> VSEntityHandler name
    val entity2Handler: Array<String>
) : SimplePacket
