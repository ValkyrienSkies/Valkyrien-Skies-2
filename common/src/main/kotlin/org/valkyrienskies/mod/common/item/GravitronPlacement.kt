package org.valkyrienskies.mod.common.item

import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc

internal object GravitronPlacement {
    fun position(eye: Vector3dc, look: Vector3dc, distance: Double, anchor: Vector3dc,
                 center: Vector3dc, scale: Vector3dc, rotation: Quaterniondc): Vector3d {
        val offset = rotation.transform(Vector3d(anchor).sub(center).mul(scale))
        return Vector3d(look).mul(distance).add(eye).sub(offset)
    }
}
