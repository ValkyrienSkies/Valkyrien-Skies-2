package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.BoxesBlockShape
import org.valkyrienskies.core.api.physics.blockstates.CollisionPoint
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.world.level.block.SlabBlockAccessor
import org.valkyrienskies.mod.mixin.accessors.world.level.block.StairBlockAccessor
import kotlin.math.roundToInt

/**
 * Utility class for collision shapes
 */
object BlockShapeUtil {

    @JvmField
    val dummyGetter = object: BlockGetter {
        override fun getHeight(): Int = 383
        override fun getMinBuildHeight(): Int = -64
        override fun getBlockEntity(blockPos: BlockPos): BlockEntity? = null
        override fun getBlockState(blockPos: BlockPos): BlockState = Blocks.VOID_AIR.defaultBlockState()
        override fun getFluidState(blockPos: BlockPos): FluidState = Fluids.EMPTY.defaultFluidState()
    }

    @JvmStatic
    fun generateCommonShapes(): Map<VoxelShape, SolidBlockShape> {
        val generatedShapes: MutableMap<VoxelShape, SolidBlockShape> = HashMap(generateOctantCollisionShapes(
            StairBlockAccessor.getBottomShapes() +
                StairBlockAccessor.getTopShapes() +
                SlabBlockAccessor.getBottomAABB() +
                SlabBlockAccessor.getTopAABB()
        ))
        generatedShapes[Shapes.block()] = fullBlockCollisionShape
        generatedShapes[Shapes.empty()] = noCollisionShape

        return generatedShapes
    }

    @JvmStatic
    /**
     * Used to generate collision shapes of blocks that can be made entirely out of octants,
     * like stairs, slabs, etc.
     *
     * Although most likely not necessary for addons, this is still left public in case it needs to be used
     */
    fun generateOctantCollisionShapes(shapes: Array<VoxelShape>): Map<VoxelShape, SolidBlockShape> {
        val testPoints = listOf(
            CollisionPoint(.25f, .25f, .25f, .25f),
            CollisionPoint(.25f, .25f, .75f, .25f),
            CollisionPoint(.25f, .75f, .25f, .25f),
            CollisionPoint(.25f, .75f, .75f, .25f),
            CollisionPoint(.75f, .25f, .25f, .25f),
            CollisionPoint(.75f, .25f, .75f, .25f),
            CollisionPoint(.75f, .75f, .25f, .25f),
            CollisionPoint(.75f, .75f, .75f, .25f),
        )

        val testBoxes = listOf(
            AABBi(0, 0, 0, 7, 7, 7),
            AABBi(0, 0, 8, 7, 7, 15),
            AABBi(0, 8, 0, 7, 15, 7),
            AABBi(0, 8, 8, 7, 15, 15),
            AABBi(8, 0, 0, 15, 7, 7),
            AABBi(8, 0, 8, 15, 7, 15),
            AABBi(8, 8, 0, 15, 15, 7),
            AABBi(8, 8, 8, 15, 15, 15),
        )

        val map: MutableMap<VoxelShape, SolidBlockShape> = HashMap()
        shapes.forEach { shape ->
            val points: MutableList<CollisionPoint> = ArrayList()
            val positiveBoxes: MutableList<AABBic> = ArrayList()
            val negativeBoxes: MutableList<AABBic> = ArrayList()

            testPoints.forEachIndexed { index, testPoint ->
                var added = false
                shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                    if (testPoint.x in minX .. maxX && testPoint.y in minY .. maxY && testPoint.z in minZ .. maxZ) {
                        points.add(testPoint)
                        added = true
                        return@forAllBoxes
                    }
                }
                if (added) {
                    positiveBoxes.add(testBoxes[index])
                } else {
                    negativeBoxes.add(testBoxes[index])
                }
            }

            val collisionShape = vsCore.newSolidStateBoxesShapeBuilder()
                .addCollisionPoints(points)
                .addPositiveBoxes(vsCore.solidShapeUtils.mergeBoxes(positiveBoxes))
                .addNegativeBoxes(vsCore.solidShapeUtils.mergeBoxes(negativeBoxes))
                .build()

            map[shape] = collisionShape
        }
        return map
    }

    @JvmStatic
    fun generateShapeFromVoxel(voxelShape: VoxelShape): BoxesBlockShape? {
        val posBoxes = ArrayList<AABBic>()
        var failed = false
        var maxBoxesToTest = 20
        voxelShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
            if (failed) {
                return@forAllBoxes
            }
            val lodMinX = (minX * 16).roundToInt()
            val lodMinY = (minY * 16).roundToInt()
            val lodMinZ = (minZ * 16).roundToInt()
            val lodMaxX = ((maxX * 16).roundToInt() - 1)
            val lodMaxY = ((maxY * 16).roundToInt() - 1)
            val lodMaxZ = ((maxZ * 16).roundToInt() - 1)
            if (lodMinX !in 0..15 || lodMinY !in 0..15 || lodMinZ !in 0..15 || lodMaxX !in 0..15 || lodMaxY !in 0..15 || lodMaxZ !in 0..15) {
                // Out of range
                failed = true
                return@forAllBoxes
            } else {
                posBoxes.add(
                    AABBi(lodMinX, lodMinY, lodMinZ, lodMaxX, lodMaxY, lodMaxZ)
                )
            }
            if (maxBoxesToTest == 0) {
                failed = true
            } else {
                maxBoxesToTest--
            }
        }
        return if (!failed) {
            try {
                vsCore.solidShapeUtils.generateShapeFromBoxes(posBoxes)
            } catch (ex: IllegalArgumentException) {
                println("WTF ERROR WHILE PROCESSING $voxelShape")
                null
            }
        } else {
            null
        }
    }

    @JvmField
    val fullLodBoundingBox: AABBi = AABBi(0, 0, 0, 15, 15, 15)

    @JvmField
    val fullBlockCollisionShape: SolidBlockShape = vsCore.newSolidStateBoxesShapeBuilder()
        .addCollisionPoints(listOf(
            CollisionPoint(.25f, .25f, .25f, .25f),
            CollisionPoint(.25f, .25f, .75f, .25f),
            CollisionPoint(.25f, .75f, .25f, .25f),
            CollisionPoint(.25f, .75f, .75f, .25f),
            CollisionPoint(.75f, .25f, .25f, .25f),
            CollisionPoint(.75f, .25f, .75f, .25f),
            CollisionPoint(.75f, .75f, .25f, .25f),
            CollisionPoint(.75f, .75f, .75f, .25f),
        ))
        .addPositiveBox(fullLodBoundingBox)
        .build()

    @JvmField
    val noCollisionShape: SolidBlockShape = vsCore.newSolidStateBoxesShapeBuilder()
        .addCollisionPoints(listOf())
        .addNegativeBox(fullLodBoundingBox)
        .build()


    @JvmStatic
    fun getShape(blockState: BlockState): VoxelShape {
        return blockState.getShape(dummyGetter, BlockPos.ZERO)
    }

    @JvmStatic
    fun getCollisionShape(blockState: BlockState): VoxelShape {
        return blockState.getCollisionShape(dummyGetter, BlockPos.ZERO)
    }

    @JvmStatic
    fun getShapeForVS(blockState: BlockState): VoxelShape {
        val collisionShape = getCollisionShape(blockState)

        return if (!collisionShape.isEmpty) {
            collisionShape
        } else {
            getShape(blockState)
        }
    }

    @JvmStatic
    fun getFluidShape(fluidState: FluidState): VoxelShape {
        return fluidState.getShape(dummyGetter, BlockPos.ZERO)
    }

}
