package org.valkyrienskies.mod.common.command

import org.joml.Quaterniond
import java.lang.Math.toRadians

data class EulerAngles(val pitchDegrees: EulerAngle, val yawDegrees: EulerAngle, val rollDegrees: EulerAngle) {
    fun toRotation(sourcePitchDegrees: Double, sourceYawDegrees: Double, sourceRollDegrees: Double): Quaterniond =
        Quaterniond().rotateXYZ(
            toRadians(pitchDegrees.getEulerAngle(sourcePitchDegrees)),
            toRadians(yawDegrees.getEulerAngle(sourceYawDegrees)),
            toRadians(rollDegrees.getEulerAngle(sourceRollDegrees)),
        )

    fun toRotationFromMCEntity(mcEntityPitch: Double, mcEntityYaw: Double) = toRotation(mcEntityPitch, -mcEntityYaw, 0.0)
}

data class EulerAngle(private val angleDegrees: Double, private val isRelative: Boolean) {
    fun getEulerAngle(sourceAngleDegrees: Double): Double {
        return if (isRelative) sourceAngleDegrees + angleDegrees else angleDegrees
    }
}
