package org.valkyrienskies.mod.common.networking

data class PlayerShipMotionSnapshot(
    val shipId: Long,
    val x: Double,
    val y: Double,
    val z: Double,
    val yRot: Double,
)

class ClientPlayerShipMotionController {
    private var wasSendingShipMotion = false

    fun nextPacket(snapshot: PlayerShipMotionSnapshot?): PacketPlayerShipMotion? {
        if (snapshot != null) {
            wasSendingShipMotion = true
            return PacketPlayerShipMotion(snapshot.shipId, snapshot.x, snapshot.y, snapshot.z, snapshot.yRot)
        }

        if (wasSendingShipMotion) {
            wasSendingShipMotion = false
            return PacketPlayerShipMotion(-1L, 0.0, 0.0, 0.0, 0.0)
        }

        return null
    }
}
