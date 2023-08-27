package org.valkyrienskies.mod.common

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3f
import org.joml.Vector3i
import org.valkyrienskies.core.apigame.world.chunks.BlockType
import org.valkyrienskies.core.impl.game.BlockTypeImpl
import org.valkyrienskies.mod.mixin.accessors.world.level.block.SlabBlockAccessor
import org.valkyrienskies.mod.mixin.accessors.world.level.block.StairBlockAccessor
import org.valkyrienskies.physics_api.Lod1BlockStateId
import org.valkyrienskies.physics_api.Lod1LiquidBlockStateId
import org.valkyrienskies.physics_api.Lod1SolidBlockStateId
import org.valkyrienskies.physics_api.voxel.CollisionPoint
import org.valkyrienskies.physics_api.voxel.Lod1LiquidBlockState
import org.valkyrienskies.physics_api.voxel.Lod1SolidBlockState
import org.valkyrienskies.physics_api.voxel.Lod1SolidBoxesCollisionShape
import org.valkyrienskies.physics_api.voxel.Lod1SolidCollisionShape
import org.valkyrienskies.physics_api.voxel.LodBlockBoundingBox
import kotlin.math.max
import kotlin.math.min

object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    private val _solidBlockStates: MutableList<Lod1SolidBlockState> = ArrayList()
    private val _liquidBlockStates: MutableList<Lod1LiquidBlockState> = ArrayList()
    private val _blockStateData: MutableList<Triple<Lod1SolidBlockStateId, Lod1LiquidBlockStateId, Lod1BlockStateId>> = ArrayList()
    private val blockStateToId: MutableMap<BlockState, BlockType> = HashMap()

    override val priority: Int
        get() = Int.MIN_VALUE
    override val solidBlockStates: List<Lod1SolidBlockState>
        get() = _solidBlockStates
    override val liquidBlockStates: List<Lod1LiquidBlockState>
        get() = _liquidBlockStates
    override val blockStateData: List<Triple<Lod1SolidBlockStateId, Lod1LiquidBlockStateId, Lod1BlockStateId>>
        get() = _blockStateData

    var registeredBlocks = false
        private set

    override fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        // By default make blocks weight 1000 kg
        return 1000.0
    }

    override fun getBlockStateType(blockState: BlockState): BlockType {
        /*
        if (blockState.isAir) return vsCore.blockTypes.air

        val blockMaterial = blockState.material
        if (blockMaterial.isLiquid)
            return if (blockMaterial == Material.LAVA) vsCore.blockTypes.lava else vsCore.blockTypes.water
        return if (blockMaterial.isSolid) vsCore.blockTypes.solid else vsCore.blockTypes.air
         */
        return blockStateToId[blockState]!!
    }

    private fun generateStairCollisionShapes(stairShapes: Array<VoxelShape>): Map<VoxelShape, Lod1SolidCollisionShape> {
        val testPoints = listOf(
            CollisionPoint(Vector3f(.25f, .25f, .25f), .25f),
            CollisionPoint(Vector3f(.25f, .25f, .75f), .25f),
            CollisionPoint(Vector3f(.25f, .75f, .25f), .25f),
            CollisionPoint(Vector3f(.25f, .75f, .75f), .25f),
            CollisionPoint(Vector3f(.75f, .25f, .25f), .25f),
            CollisionPoint(Vector3f(.75f, .25f, .75f), .25f),
            CollisionPoint(Vector3f(.75f, .75f, .25f), .25f),
            CollisionPoint(Vector3f(.75f, .75f, .75f), .25f),
        )

        val testBoxes = listOf(
            LodBlockBoundingBox.createVSBoundingBox(0, 0, 0, 7, 7, 7),
            LodBlockBoundingBox.createVSBoundingBox(0, 0, 8, 7, 7, 15),
            LodBlockBoundingBox.createVSBoundingBox(0, 8, 0, 7, 15, 7),
            LodBlockBoundingBox.createVSBoundingBox(0, 8, 8, 7, 15, 15),
            LodBlockBoundingBox.createVSBoundingBox(8, 0, 0, 15, 7, 7),
            LodBlockBoundingBox.createVSBoundingBox(8, 0, 8, 15, 7, 15),
            LodBlockBoundingBox.createVSBoundingBox(8, 8, 0, 15, 15, 7),
            LodBlockBoundingBox.createVSBoundingBox(8, 8, 8, 15, 15, 15),
        )

        val map: MutableMap<VoxelShape, Lod1SolidCollisionShape> = HashMap()
        stairShapes.forEach { stairShape ->
            val points: MutableList<CollisionPoint> = ArrayList()
            val positiveBoxes: MutableList<LodBlockBoundingBox> = ArrayList()
            val negativeBoxes: MutableList<LodBlockBoundingBox> = ArrayList()
            testPoints.forEachIndexed { index, testPoint ->
                var added = false
                stairShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                    if (testPoint.pos.x() in minX .. maxX && testPoint.pos.y() in minY .. maxY && testPoint.pos.z() in minZ .. maxZ) {
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
            val minTotalAABB = Vector3i(positiveBoxes[0].minX.toInt(), positiveBoxes[0].minY.toInt(), positiveBoxes[0].minZ.toInt())
            val maxTotalAABB = Vector3i(positiveBoxes[0].maxX.toInt(), positiveBoxes[0].maxY.toInt(), positiveBoxes[0].maxZ.toInt())
            for (i in 1 until positiveBoxes.size) {
                minTotalAABB.x = min(minTotalAABB.x, positiveBoxes[i].minX.toInt())
                minTotalAABB.y = min(minTotalAABB.y, positiveBoxes[i].minY.toInt())
                minTotalAABB.z = min(minTotalAABB.z, positiveBoxes[i].minZ.toInt())
                maxTotalAABB.x = max(maxTotalAABB.x, positiveBoxes[i].maxX.toInt())
                maxTotalAABB.y = max(maxTotalAABB.y, positiveBoxes[i].maxY.toInt())
                maxTotalAABB.z = max(maxTotalAABB.z, positiveBoxes[i].maxZ.toInt())
            }
            val overallBox = LodBlockBoundingBox.createVSBoundingBox(
                minTotalAABB.x.toByte(), minTotalAABB.y.toByte(), minTotalAABB.z.toByte(), maxTotalAABB.x.toByte(),
                maxTotalAABB.y.toByte(), maxTotalAABB.z.toByte()
            )
            // TODO: Merge boxes (later)
            val collisionShape = Lod1SolidBoxesCollisionShape(
                overallBoundingBox = overallBox,
                collisionPoints = points,
                solidBoxes = positiveBoxes,
                negativeBoxes = negativeBoxes,
            )
            map[stairShape] = collisionShape
        }
        return map
    }

    fun registerAllBlockStates(blockStates: Iterable<BlockState>) {
        val fullLodBoundingBox = LodBlockBoundingBox.createVSBoundingBox(0, 0, 0, 15, 15, 15)
        val fullBlockCollisionPoints = listOf(
            CollisionPoint(Vector3f(.25f, .25f, .25f), .25f),
            CollisionPoint(Vector3f(.25f, .25f, .75f), .25f),
            CollisionPoint(Vector3f(.25f, .75f, .25f), .25f),
            CollisionPoint(Vector3f(.25f, .75f, .75f), .25f),
            CollisionPoint(Vector3f(.75f, .25f, .25f), .25f),
            CollisionPoint(Vector3f(.75f, .25f, .75f), .25f),
            CollisionPoint(Vector3f(.75f, .75f, .25f), .25f),
            CollisionPoint(Vector3f(.75f, .75f, .75f), .25f),
        )
        val fullBlockCollisionShape = Lod1SolidBoxesCollisionShape(
            overallBoundingBox = fullLodBoundingBox,
            collisionPoints = fullBlockCollisionPoints,
            solidBoxes = listOf(fullLodBoundingBox),
            negativeBoxes = listOf(),
        )

        val voxelShapeToCollisionShapeMap = generateStairCollisionShapes(
            StairBlockAccessor.getTopShapes() + StairBlockAccessor.getBottomShapes() + SlabBlockAccessor.getBottomAABB() + SlabBlockAccessor.getTopAABB()
        )

        val waterBlockState = Lod1LiquidBlockState(
            boundingBox = fullLodBoundingBox,
            density = 1000.0f,
            dragCoefficient = 0.3f,
            fluidVel = Vector3f(),
            lod1LiquidBlockStateId = BlockTypeImpl.WATER.toInt(),
        )

        val lavaBlockState = Lod1LiquidBlockState(
            boundingBox = fullLodBoundingBox,
            density = 10000.0f,
            dragCoefficient = 0.3f,
            fluidVel = Vector3f(),
            lod1LiquidBlockStateId = BlockTypeImpl.LAVA.toInt(),
        )

        // Add water/lava block states
        _liquidBlockStates.add(waterBlockState)
        _liquidBlockStates.add(lavaBlockState)

        _blockStateData.add(Triple(BlockTypeImpl.AIR.toInt(), BlockTypeImpl.WATER.toInt(), BlockTypeImpl.WATER.toInt()))
        _blockStateData.add(Triple(BlockTypeImpl.AIR.toInt(), BlockTypeImpl.LAVA.toInt(), BlockTypeImpl.LAVA.toInt()))

        // Setup initial conditions for future ids
        var nextSolidId = 1
        var nextFluidId = 4
        var nextVoxelStateId = 4

        val dummyBlockGetter = object: BlockGetter {
            override fun getHeight(): Int = 255

            override fun getMinBuildHeight(): Int = 0

            override fun getBlockEntity(blockPos: BlockPos): BlockEntity? = null

            override fun getBlockState(blockPos: BlockPos): BlockState = Blocks.VOID_AIR.defaultBlockState()

            override fun getFluidState(blockPos: BlockPos): FluidState = Fluids.EMPTY.defaultFluidState()
        }

        // Manually define stairs/slab shapes, because VoxelShapes sus T_T

        blockStates.forEach { blockState: BlockState ->
            val blockType: BlockType
            if (blockState.isAir) {
                blockType = vsCore.blockTypes.air
            } else {
                val blockMaterial = blockState.material
                blockType = if (blockMaterial.isLiquid) {
                    if (blockMaterial == Material.LAVA) {
                        vsCore.blockTypes.lava
                    } else {
                        vsCore.blockTypes.water
                    }
                } else if (blockMaterial.isSolid) {
                    val voxelShape = blockState.getShape(dummyBlockGetter, BlockPos.ZERO)

                    val collisionShape: Lod1SolidCollisionShape = if (voxelShapeToCollisionShapeMap.contains(voxelShape)) {
                        voxelShapeToCollisionShapeMap[voxelShape]!!
                    } else {
                        fullBlockCollisionShape
                    }

                    val solidStateId = nextSolidId++
                    val newSolidBlockState = Lod1SolidBlockState(
                        collisionShape = collisionShape,
                        elasticity = 0.3f,
                        friction = 1.0f,
                        hardness = 1.0f,
                        lod1SolidBlockStateId = solidStateId,
                    )
                    _solidBlockStates.add(newSolidBlockState)

                    val blockStateId = nextVoxelStateId++
                    // TODO: For now don't waterlog, in the future add the fluid state id here
                    _blockStateData.add(Triple(solidStateId, BlockTypeImpl.AIR.toInt(), blockStateId))
                    BlockTypeImpl(blockStateId)
                } else {
                    vsCore.blockTypes.air
                }
            }
            blockStateToId[blockState] = blockType
        }

        registeredBlocks = true
    }
}
