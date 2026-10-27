package org.valkyrienskies.mod.common.debug

/** Keep the creation time and the parent ID when the server saves a ship. */
class ShipDebugMetadata(
    val createdGameTime: Long = 0,
    val creationTimeKnown: Boolean = false,
    val parentShipId: Long? = null,
)
