package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import org.joml.Matrix4d
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.mod.common.util.multiply

/**
 * @param renderTransform The ship's render transform
 * @param matrix          The {@link PoseStack} we are multiplying
 * @param blockPos        The position of the block in question
 * @param camX            Player camera X
 * @param camY            Player camera Y
 * @param camZ            Player camera Z
 */
fun transformRenderWithShip(
    renderTransform: ShipTransform, matrix: PoseStack,
    blockPos: BlockPos,
    camX: Double, camY: Double, camZ: Double
) {
    val shipToWorldMatrix = renderTransform.shipToWorldMatrix

    // Create the render matrix from the render transform and player position
    val renderMatrix = Matrix4d()
    renderMatrix.translate(-camX, -camY, -camZ)
    renderMatrix.mul(shipToWorldMatrix)
    renderMatrix.translate(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())

    // Apply the render matrix to the
    matrix.multiply(renderMatrix, renderTransform.shipCoordinatesToWorldCoordinatesRotation)
}
