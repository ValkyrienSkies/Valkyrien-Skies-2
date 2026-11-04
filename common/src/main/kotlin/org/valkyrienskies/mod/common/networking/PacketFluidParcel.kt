package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import java.util.UUID

data class FluidParcelState(
    val fluid: String,
    val x: Double, val y: Double, val z: Double,
    val velocityX: Double, val velocityY: Double, val velocityZ: Double,
    val volume: Double,
)

data class PacketFluidParcel(
    val id: UUID,
    val dimension: String,
    val revision: Long,
    val state: FluidParcelState?,
) : SimplePacket

data class FluidParcelUpdate(val id: UUID, val revision: Long, val state: FluidParcelState?)

data class PacketFluidParcelBatch(val dimension: String, val updates: List<FluidParcelUpdate>) : SimplePacket {
    init { require(updates.size in 2..MAX_UPDATES) }

    internal fun packets() = updates.map { PacketFluidParcel(it.id, dimension, it.revision, it.state) }

    companion object {
        internal const val MAX_UPDATES = 256
    }
}
