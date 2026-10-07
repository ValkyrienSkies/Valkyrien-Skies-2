package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.core.internal.physics.VsiDebugContact
import org.valkyrienskies.core.internal.physics.VsiDebugJoint
import org.valkyrienskies.core.internal.physics.VsiDebugShip
import org.valkyrienskies.core.internal.physics.VsiDebugVector
import org.valkyrienskies.mod.common.debug.DebugRendererMode

data class DebugShipInfo(
    val physics: VsiDebugShip,
    val ageTicks: Long,
    val creationTimeKnown: Boolean,
    val parentShipId: Long?,
)

data class PacketPhysicsDebug(
    val mode: DebugRendererMode = DebugRendererMode.NONE,
    val dimensionId: String = "",
    val ships: List<DebugShipInfo> = emptyList(),
    val contacts: List<VsiDebugContact> = emptyList(),
    val joints: List<VsiDebugJoint> = emptyList(),
    val origins: List<VsiDebugVector> = emptyList(),
    val limited: Boolean = false,
    val physicsTick: Int = 0,
) : SimplePacket
