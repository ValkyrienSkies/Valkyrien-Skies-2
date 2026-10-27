package org.valkyrienskies.mod.common.assembly

import org.joml.Matrix4dc
import org.joml.Vector3d
import org.joml.Vector3dc

internal object WeldKinematics {
    data class Center(val position: Vector3d, val velocity: Vector3d)

    fun rebase(toWorld: Matrix4dc, oldWorldCenter: Vector3dc, oldVelocity: Vector3dc,
               angularVelocity: Vector3dc, newModelCenter: Vector3dc): Center {
        val position = toWorld.transformPosition(Vector3d(newModelCenter))
        val velocity = Vector3d(oldVelocity).add(Vector3d(angularVelocity).cross(Vector3d(position).sub(oldWorldCenter)))
        return Center(position, velocity)
    }
}
