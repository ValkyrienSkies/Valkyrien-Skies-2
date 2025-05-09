package org.valkyrienskies.mod.common.entity

import org.joml.Vector3d

interface ShipyardPosSavable {
    fun `valkyrienskies$getUnloadedShipyardPos`(): Vector3d?
    fun `valkyrienskies$setUnloadedShipyardPos`(vector3d: Vector3d?)
}
