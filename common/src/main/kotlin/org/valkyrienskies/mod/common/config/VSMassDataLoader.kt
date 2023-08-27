package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.tags.TagKey
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
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
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.core.impl.game.BlockTypeImpl
import org.valkyrienskies.mod.common.BlockStateInfoProvider
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.world.level.block.SlabBlockAccessor
import org.valkyrienskies.mod.mixin.accessors.world.level.block.StairBlockAccessor
import org.valkyrienskies.mod.util.logger
import org.valkyrienskies.physics_api.Lod1BlockStateId
import org.valkyrienskies.physics_api.Lod1LiquidBlockStateId
import org.valkyrienskies.physics_api.Lod1SolidBlockStateId
import org.valkyrienskies.physics_api.voxel.CollisionPoint
import org.valkyrienskies.physics_api.voxel.Lod1LiquidBlockState
import org.valkyrienskies.physics_api.voxel.Lod1SolidBlockState
import org.valkyrienskies.physics_api.voxel.Lod1SolidBoxesCollisionShape
import org.valkyrienskies.physics_api.voxel.Lod1SolidCollisionShape
import org.valkyrienskies.physics_api.voxel.LodBlockBoundingBox
import java.util.Optional
import kotlin.math.max
import kotlin.math.min

private data class VSBlockStateInfo(
    val id: ResourceLocation,
    val priority: Int,
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
    val type: VSBlockType?,
)

object MassDatapackResolver : BlockStateInfoProvider {
    private val map = hashMapOf<ResourceLocation, VSBlockStateInfo>()
    private val _solidBlockStates: MutableList<Lod1SolidBlockState> = ArrayList()
    private val _liquidBlockStates: MutableList<Lod1LiquidBlockState> = ArrayList()
    private val _blockStateData: MutableList<Triple<Lod1SolidBlockStateId, Lod1LiquidBlockStateId, Lod1BlockStateId>> = ArrayList()
    private val blockStateToId: MutableMap<BlockState, BlockType> = HashMap()

    val loader get() = VSMassDataLoader()

    private const val DEFAULT_ELASTICITY = 0.3
    private const val DEFAULT_FRICTION = 0.5
    // Unused for now, placeholder for later
    private const val DEFAULT_HARDNESS = 1.0

    override val priority: Int
        get() = 100

    override fun getBlockStateMass(blockState: BlockState): Double? =
        map[Registry.BLOCK.getKey(blockState.block)]?.mass

    override fun getBlockStateType(blockState: BlockState): VSBlockType? =
        blockStateToId[blockState]!!

    var registeredBlocks = false
        private set

    override val solidBlockStates: List<Lod1SolidBlockState>
        get() = _solidBlockStates
    override val liquidBlockStates: List<Lod1LiquidBlockState>
        get() = _liquidBlockStates
    override val blockStateData: List<Triple<Lod1SolidBlockStateId, Lod1LiquidBlockStateId, Lod1BlockStateId>>
        get() = _blockStateData

    class VSMassDataLoader : SimpleJsonResourceReloadListener(Gson(), "vs_mass") {
        private val tags = mutableListOf<VSBlockStateInfo>()

        override fun apply(
            objects: MutableMap<ResourceLocation, JsonElement>?,
            resourceManager: ResourceManager?,
            profiler: ProfilerFiller?
        ) {
            map.clear()
            tags.clear()
            objects?.forEach { (location, element) ->
                try {
                    if (element.isJsonArray) {
                        element.asJsonArray.forEach { element1: JsonElement ->
                            parse(element1, location)
                        }
                    } else if (element.isJsonObject) {
                        parse(element, location)
                    } else throw IllegalArgumentException()
                } catch (e: Exception) {
                    logger.error(e)
                }
            }
        }

        init {
            VSGameEvents.tagsAreLoaded.on { _, _ ->
                tags.forEach { tagInfo ->
                    val tag: Optional<HolderSet.Named<Block>>? =
                        Registry.BLOCK.getTag(TagKey.create(Registry.BLOCK_REGISTRY, tagInfo.id))
                    if (tag != null) {

                        if (!tag.isPresent()) {
                            logger.warn("No specified tag '${tagInfo.id}' doesn't exist!")
                            return@forEach
                        }

                        tag.get().forEach {
                            add(
                                VSBlockStateInfo(
                                    Registry.BLOCK.getKey(it.value()), tagInfo.priority, tagInfo.mass, tagInfo.friction,
                                    tagInfo.elasticity, tagInfo.type
                                )
                            )
                        }
                    }
                }
            }
        }

        // so why does this exist? cus for some reason initializes their tags after all the other things
        // idk why, so we note them down and use them later
        private fun addToBeAddedTags(tag: VSBlockStateInfo) {
            tags.add(tag)
        }

        private fun add(info: VSBlockStateInfo) {
            if (map.containsKey(info.id)) {
                if (map[info.id]!!.priority < info.priority) {
                    map[info.id] = info
                }
            } else {
                map[info.id] = info
            }
        }

        private fun parse(element: JsonElement, origin: ResourceLocation) {
            val tag = element.asJsonObject["tag"]?.asString
            val weight = element.asJsonObject["mass"]?.asDouble
                ?: throw IllegalArgumentException("No mass in file $origin")
            val friction = element.asJsonObject["friction"]?.asDouble ?: DEFAULT_FRICTION
            val elasticityOriginal = element.asJsonObject["elasticity"]
            if (elasticityOriginal != null) {
                println("amogus")
            }
            val elasticity = element.asJsonObject["elasticity"]?.asDouble ?: DEFAULT_ELASTICITY

            val priority = element.asJsonObject["priority"]?.asInt ?: 100

            if (tag != null) {
                addToBeAddedTags(VSBlockStateInfo(ResourceLocation(tag), priority, weight, friction, elasticity, null))
            } else {
                val block = element.asJsonObject["block"]?.asString
                    ?: throw IllegalArgumentException("No block or tag in file $origin")

                add(VSBlockStateInfo(ResourceLocation(block), priority, weight, friction, elasticity, null))
            }
        }
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

        // Add default block states
        run {
            // region Add default solid block state
            val solidBlockState = Lod1SolidBlockState(
                collisionShape = fullBlockCollisionShape,
                elasticity = DEFAULT_ELASTICITY.toFloat(),
                friction = DEFAULT_FRICTION.toFloat(),
                hardness = DEFAULT_HARDNESS.toFloat(),
                lod1SolidBlockStateId = BlockTypeImpl.SOLID.toInt(),
            )
            _solidBlockStates.add(solidBlockState)
            _blockStateData.add(Triple(BlockTypeImpl.SOLID.toInt(), BlockTypeImpl.AIR.toInt(), BlockTypeImpl.SOLID.toInt()))
            // endregion

            // region Add default water/lava liquid block states
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

            _liquidBlockStates.add(waterBlockState)
            _liquidBlockStates.add(lavaBlockState)
            _blockStateData.add(Triple(BlockTypeImpl.AIR.toInt(), BlockTypeImpl.WATER.toInt(), BlockTypeImpl.WATER.toInt()))
            _blockStateData.add(Triple(BlockTypeImpl.AIR.toInt(), BlockTypeImpl.LAVA.toInt(), BlockTypeImpl.LAVA.toInt()))
            // endregion
        }

        // A dummy world used to get the VoxelShape for each block state
        val dummyBlockGetter = object: BlockGetter {
            override fun getHeight(): Int = 255

            override fun getMinBuildHeight(): Int = 0

            override fun getBlockEntity(blockPos: BlockPos): BlockEntity? = null

            override fun getBlockState(blockPos: BlockPos): BlockState = Blocks.VOID_AIR.defaultBlockState()

            override fun getFluidState(blockPos: BlockPos): FluidState = Fluids.EMPTY.defaultFluidState()
        }

        // Create a map of common VoxelShape to Lod1SolidCollisionShape
        val voxelShapeToCollisionShapeMap = generateStairCollisionShapes(
            StairBlockAccessor.getTopShapes() + StairBlockAccessor.getBottomShapes() + SlabBlockAccessor.getBottomAABB() + SlabBlockAccessor.getTopAABB()
        )

        // Setup initial conditions for future ids
        var nextSolidId = 2
        var nextFluidId = 4
        var nextVoxelStateId = 4

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

                    val vsBlockStateInfo = map[Registry.BLOCK.getKey(blockState.block)]

                    // Create new solid block state
                    val solidStateId = nextSolidId++
                    val newSolidBlockState = Lod1SolidBlockState(
                        collisionShape = collisionShape,
                        elasticity = vsBlockStateInfo?.elasticity?.toFloat() ?: DEFAULT_ELASTICITY.toFloat(),
                        friction = vsBlockStateInfo?.friction?.toFloat() ?: DEFAULT_FRICTION.toFloat(),
                        hardness = DEFAULT_HARDNESS.toFloat(),
                        lod1SolidBlockStateId = solidStateId,
                    )
                    _solidBlockStates.add(newSolidBlockState)

                    // Create new voxel state
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

    private val logger by logger()
}
