package org.valkyrienskies.mod.common

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.block.RenderShape
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import net.minecraft.world.entity.Entity
import org.valkyrienskies.mod.common.util.ShipyardEntityQueryContext
import org.valkyrienskies.mod.common.world.forEachRayImage
import org.valkyrienskies.mod.common.util.multiply
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames

object VSClientGameUtils {

    /** Supplement Create's candidate list only at physical mouths; do not enlarge unrelated
     * gameplay entity queries or merge two disjoint local AABBs into one huge box. */
    @JvmStatic
    fun <E : Entity> glueCandidates(level: ClientLevel, type: Class<E>, worldBox: AABB, original: List<E>): List<E> {
        if (level.allShips.getSplitShipIds().isEmpty()) return original
        var result: LinkedHashSet<E>? = null
        val bounds = worldBox.toJOML()
        for (piece in level.allShips.getIntersectingPieces(bounds, level.dimensionId)) {
            if (level.allShips.getSplitPieces(piece.shipId).isEmpty()) continue
            val local = AABBd(bounds).transform(piece.worldToShip).toMinecraft()
            ShipyardEntityQueryContext.pushShipSpaceQuery(local, piece.shipId)
            val matches = try { level.getEntitiesOfClass(type, local) } finally { ShipyardEntityQueryContext.popShipSpaceQuery() }
            if (matches.isNotEmpty()) {
                val merged = result ?: LinkedHashSet(original).also { result = it }
                merged.addAll(matches)
            }
        }
        return result?.toList() ?: original
    }

    @JvmStatic
    fun clipGlueCandidate(level: ClientLevel, ship: ClientShip, box: AABB, from: Vec3, to: Vec3): java.util.Optional<Vec3> {
        var best: Vec3? = null
        var distance = Double.POSITIVE_INFINITY
        level.forEachRayImage(ship, from, to, false) { image ->
            image.clip(box, 0.0, false)?.let(image::world)?.let { point ->
                val d = from.distanceToSqr(point)
                if (d < distance) { distance = d; best = point }
            }
        }
        return java.util.Optional.ofNullable(best)
    }

    @JvmStatic
    fun viewBlockingShipBlock(level: ClientLevel, x: Double, y: Double, z: Double): BlockPos? {
        val point = Vector3d(x, y, z)
        fun blocksView(pos: BlockPos): Boolean {
            val state = level.getBlockState(pos)
            return state.renderShape != RenderShape.INVISIBLE && state.isViewBlocking(level, pos)
        }
        for (ship in level.getRenderShipsIntersecting(AABBd(x, y, z, x, y, z))) {
            val pieces = level.allShips.getSplitPieces(ship.id)
            if (pieces.isEmpty()) {
                val local = ship.renderTransform.worldToShip.transformPosition(point, Vector3d())
                val pos = BlockPos.containing(local.x, local.y, local.z)
                if (blocksView(pos)) return pos
            } else for (piece in pieces) {
                if (piece.dimensionId != level.dimensionId) continue
                val local = ShipPieceRenderFrames.rendered(piece).blockPositionAt(point) ?: continue
                val pos = BlockPos(local.x, local.y, local.z)
                if (blocksView(pos)) return pos
            }
        }
        return null
    }

    @JvmStatic
    fun multiplyWithShipToWorld(poseStack: PoseStack, ship: ClientShip) {
        poseStack.multiply(ship.renderTransform.shipToWorld, ship.renderTransform.shipToWorldRotation)
    }

    @JvmStatic
    fun transformRenderIfInShipyard(poseStack: PoseStack, offsetX: Double, offsetY: Double, offsetZ: Double) {
        val ship = Minecraft.getInstance().level?.getLoadedShipManagingPos(offsetX, offsetY, offsetZ)

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
        return Minecraft.getInstance().level?.getLoadedShipManagingPos(offsetX, offsetY, offsetZ)
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
