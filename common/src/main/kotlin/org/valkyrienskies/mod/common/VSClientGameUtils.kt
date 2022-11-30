package org.valkyrienskies.mod.common

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.mod.common.util.multiply
import com.mojang.math.Matrix4f as Matrix4fMC

object VSClientGameUtils {
    /**
     * Modify the last transform of [poseStack] to be the following:
     *
     * Last = Last * translate(-[camX], -[camY], -[camZ]) * [renderTransform] * translate([offsetX], [offsetY], [offsetZ])
     */
    @JvmStatic
    fun transformRenderWithShip(
        renderTransform: ShipTransform,
        poseStack: PoseStack,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        camX: Double,
        camY: Double,
        camZ: Double
    ) {
        val shipToWorldMatrix = renderTransform.shipToWorld

        // Create the render matrix from the render transform and player position
        val renderMatrix = Matrix4d()
        renderMatrix.translate(-camX, -camY, -camZ)
        renderMatrix.mul(shipToWorldMatrix)
        renderMatrix.translate(offsetX, offsetY, offsetZ)

        // Multiply the last transform of [poseStack] by [shipToWorldMatrix]
        poseStack.multiply(renderMatrix, renderTransform.shipToWorldRotation)
    }

    @JvmStatic
    fun transformRenderWithShip(
        renderTransform: ShipTransform,
        matrix: Matrix4fMC,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        camX: Double,
        camY: Double,
        camZ: Double
    ) {
        val shipToWorldMatrix = renderTransform.shipToWorld

        // Create the render matrix from the render transform and player position
        val renderMatrix = Matrix4d()
        renderMatrix.translate(-camX, -camY, -camZ)
        renderMatrix.mul(shipToWorldMatrix)
        renderMatrix.translate(offsetX, offsetY, offsetZ)

        // Multiply the last transform of [poseStack] by [shipToWorldMatrix]
        matrix.multiply(renderMatrix)
    }

    @JvmStatic
    fun transformRenderWithShip(
        renderTransform: ShipTransform,
        matrix: Matrix4f,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        camX: Double,
        camY: Double,
        camZ: Double
    ) {
        val shipToWorldMatrix = renderTransform.shipToWorld

        // Create the render matrix from the render transform and player position
        val renderMatrix = Matrix4d()
        renderMatrix.translate(-camX, -camY, -camZ)
        renderMatrix.mul(shipToWorldMatrix)
        renderMatrix.translate(offsetX, offsetY, offsetZ)

        // Multiply the last transform of [poseStack] by [shipToWorldMatrix]
        matrix.mul(Matrix4f(renderMatrix))
    }
}
