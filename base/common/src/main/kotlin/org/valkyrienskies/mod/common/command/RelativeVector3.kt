package org.valkyrienskies.mod.common.command

import org.joml.Quaterniond
import org.joml.Vector3d
import java.lang.Math.toRadians

data class RelativeVector3(val x: RelativeValue, val y: RelativeValue, val z: RelativeValue) {
    fun toEulerRotation(sourcePitchDegrees: Double, sourceYawDegrees: Double, sourceRollDegrees: Double): Quaterniond =
        Quaterniond().rotateXYZ(
            toRadians(x.getRelativeValue(sourcePitchDegrees)),
            toRadians(y.getRelativeValue(sourceYawDegrees)),
            toRadians(z.getRelativeValue(sourceRollDegrees)),
        )

    fun toEulerRotationFromMCEntity(mcEntityPitch: Double, mcEntityYaw: Double) =
        toEulerRotation(mcEntityPitch, -mcEntityYaw, 0.0)

    fun toVector3d(sourceX: Double, sourceY: Double, sourceZ: Double): Vector3d = Vector3d(
        x.getRelativeValue(sourceX), y.getRelativeValue(sourceY), z.getRelativeValue(sourceZ)
    )
}

data class RelativeValue(private val angleDegrees: Double, private val isRelative: Boolean) {
    fun getRelativeValue(sourceAngleDegrees: Double): Double {
        return if (isRelative) sourceAngleDegrees + angleDegrees else angleDegrees
    }
}
