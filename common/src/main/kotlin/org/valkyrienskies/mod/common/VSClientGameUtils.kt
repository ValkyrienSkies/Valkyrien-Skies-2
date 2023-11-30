package org.valkyrienskies.mod.common

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.mod.common.util.multiply
import com.mojang.math.Matrix4f as Matrix4fMC

object VSClientGameUtils {

    @JvmStatic
    fun multiplyWithShipToWorld(poseStack: PoseStack, ship: ClientShip) {
        poseStack.multiply(ship.renderTransform.shipToWorld, ship.renderTransform.shipToWorldRotation)
    }

    @JvmStatic
    fun transformRenderIfInShipyard(poseStack: PoseStack, offsetX: Double, offsetY: Double, offsetZ: Double) {
        val ship = Minecraft.getInstance().level?.getShipObjectManagingPos(offsetX, offsetY, offsetZ)

        if (ship != null) {
            val transform = ship.renderTransform
            val renderMatrix = Matrix4d()
                .mul(transform.shipToWorld)
                .translate(offsetX, offsetY, offsetZ)

            poseStack.multiply(renderMatrix)
        } else {
            poseStack.translate(offsetX, offsetY, offsetZ)
        }
    }

    @JvmStatic
    fun getClientShip(offsetX: Double, offsetY: Double, offsetZ: Double): ClientShip? {
        return Minecraft.getInstance().level?.getShipObjectManagingPos(offsetX, offsetY, offsetZ)
    }

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

    /**
     * @param renderTransform The ship's render transform
     * @param matrix          The {@link PoseStack} we are multiplying
     * @param blockPos        The position of the block in question
     * @param camX            Player camera X
     * @param camY            Player camera Y
     * @param camZ            Player camera Z
     */
    @JvmStatic
    fun transformRenderWithShip(
        renderTransform: ShipTransform,
        matrix: PoseStack,
        blockPos: BlockPos,
        camX: Double,
        camY: Double,
        camZ: Double
    ) {
        transformRenderWithShip(
            renderTransform,
            matrix,
            blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(),
            camX, camY, camZ
        )
    }
}

