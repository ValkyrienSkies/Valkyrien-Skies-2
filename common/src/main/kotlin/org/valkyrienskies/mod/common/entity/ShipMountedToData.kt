package org.valkyrienskies.mod.common.entity

import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.LoadedShip

data class ShipMountedToData(
    val shipMountedTo: LoadedShip,
    val mountPosInShip: Vector3dc,
)
