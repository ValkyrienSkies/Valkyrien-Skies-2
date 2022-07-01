package org.valkyrienskies.mod.common

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4d
import org.valkyrienskies.core.game.ships.ShipTransform
import org.valkyrienskies.mod.common.util.multiply

object VSClientGameUtils {
    /**
     * Modify the last transform of [poseStack] to be the following:
     *
     * Last = Last * translate(-[camX], -[camY], -[camZ]) * [renderTransform] * translate([offsetX], [offsetY], [offsetZ])
     */
    @JvmStatic
    fun transformRenderWithShip(
        renderTransform: ShipTransform, poseStack: PoseStack,
        offsetX: Double, offsetY: Double, offsetZ: Double,
        camX: Double, camY: Double, camZ: Double
    ) {
        val shipToWorldMatrix = renderTransform.shipToWorldMatrix

        // Create the render matrix from the render transform and player position
        val renderMatrix = Matrix4d()
        renderMatrix.translate(-camX, -camY, -camZ)
        renderMatrix.mul(shipToWorldMatrix)
        renderMatrix.translate(offsetX, offsetY, offsetZ)

        // Apply the render matrix to the
        poseStack.multiply(renderMatrix, renderTransform.shipCoordinatesToWorldCoordinatesRotation)
    }
}
