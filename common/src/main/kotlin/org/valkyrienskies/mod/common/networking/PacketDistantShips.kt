package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketShipRenderDistance(val blocks: Int, val dimension: String) : SimplePacket

data class PacketDistantShip(
    val shipId: Long,
    val dimension: String,
    val revision: Int,
    val shipAABB: IntArray,
    val transform: DoubleArray,
    val blocks: ByteArray,
) : SimplePacket {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketDistantShip) return false
        return shipId == other.shipId && dimension == other.dimension && revision == other.revision &&
            shipAABB.contentEquals(other.shipAABB) && transform.contentEquals(other.transform) &&
            blocks.contentEquals(other.blocks)
    }

    override fun hashCode(): Int {
        var result = shipId.hashCode()
        result = 31 * result + dimension.hashCode()
        result = 31 * result + revision
        result = 31 * result + shipAABB.contentHashCode()
        result = 31 * result + transform.contentHashCode()
        result = 31 * result + blocks.contentHashCode()
        return result
    }
}

/** Server to client: new transforms of moving distant ships. [transforms] holds [DistantShipTransforms.SIZE] values per ship. */
data class PacketDistantShipTransforms(
    val dimension: String,
    val shipIds: LongArray,
    val transforms: DoubleArray,
) : SimplePacket {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketDistantShipTransforms) return false
        return dimension == other.dimension && shipIds.contentEquals(other.shipIds) &&
            transforms.contentEquals(other.transforms)
    }

    override fun hashCode(): Int =
        31 * (31 * dimension.hashCode() + shipIds.contentHashCode()) + transforms.contentHashCode()
}

/** Server to client: stop drawing these distant ships. */
data class PacketRemoveDistantShips(val shipIds: LongArray) : SimplePacket {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketRemoveDistantShips) return false
        return shipIds.contentEquals(other.shipIds)
    }

    override fun hashCode(): Int = shipIds.contentHashCode()
}

/**
 * Layout of a ship transform in distant ship packets: position in the world (3), rotation quaternion x, y, z, w (4),
 * scaling (3), and position in the shipyard (3).
 */
object DistantShipTransforms {
    const val SIZE = 13
    const val POSITION = 0
    const val ROTATION = 3
    const val SCALING = 7
    const val POSITION_IN_MODEL = 10
}
