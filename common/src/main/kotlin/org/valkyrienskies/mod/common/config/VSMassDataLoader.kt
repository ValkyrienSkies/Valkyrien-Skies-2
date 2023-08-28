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
import org.joml.primitives.AABBi
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
import org.valkyrienskies.physics_api.voxel.LodBlockBoundingBox.Companion
import java.util.Optional
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    // Limits set by Krunch
    private const val MAX_BOXES = 10
    private const val MAX_POINTS = 20

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

    private fun mergeBoxes(boxes: MutableList<LodBlockBoundingBox>): MutableList<LodBlockBoundingBox> {
        fun tryMerging(box0: LodBlockBoundingBox, box1: LodBlockBoundingBox): LodBlockBoundingBox? {
            // Check x-axis
            if (box0.minY == box1.minY && box0.maxY == box1.maxY && box0.minZ == box1.minZ && box0.maxZ == box1.maxZ) {
                val box0MinX = box0.minX
                val box0MaxX = box0.maxX + 1
                val box1MinX = box1.minX
                val box1MaxX = box1.maxX + 1

                if (box0MinX <= box1MaxX && box0MaxX >= box1MinX) {
                    return LodBlockBoundingBox.createVSBoundingBox(
                        min(box0.minX.toInt(), box1.minX.toInt()).toByte(),
                        box0.minY,
                        box0.minZ,
                        max(box0.maxX.toInt(), box1.maxX.toInt()).toByte(),
                        box0.maxY,
                        box0.maxZ,
                    )
                }
            }

            // Check y-axis
            if (box0.minX == box1.minX && box0.maxX == box1.maxX && box0.minZ == box1.minZ && box0.maxZ == box1.maxZ) {
                val box0MinY = box0.minY
                val box0MaxY = box0.maxY + 1
                val box1MinY = box1.minY
                val box1MaxY = box1.maxY + 1

                if (box0MinY <= box1MaxY && box0MaxY >= box1MinY) {
                    return LodBlockBoundingBox.createVSBoundingBox(
                        box0.minX,
                        min(box0.minY.toInt(), box1.minY.toInt()).toByte(),
                        box0.minZ,
                        box0.maxX,
                        max(box0.maxY.toInt(), box1.maxY.toInt()).toByte(),
                        box0.maxZ,
                    )
                }
            }

            // Check z-axis
            if (box0.minX == box1.minX && box0.maxX == box1.maxX && box0.minY == box1.minY && box0.maxY == box1.maxY) {
                val box0MinZ = box0.minZ
                val box0MaxZ = box0.maxZ + 1
                val box1MinZ = box1.minZ
                val box1MaxZ = box1.maxZ + 1

                if (box0MinZ <= box1MaxZ && box0MaxZ >= box1MinZ) {
                    return LodBlockBoundingBox.createVSBoundingBox(
                        box0.minX,
                        box0.minY,
                        min(box0.minZ.toInt(), box1.minZ.toInt()).toByte(),
                        box0.maxX,
                        box0.maxY,
                        max(box0.maxZ.toInt(), box1.maxZ.toInt()).toByte(),
                    )
                }
            }

            return null
        }

        if (boxes.size < 2) return boxes
        var done = false
        loop@while (!done) {
            for (i in 0 until boxes.size) {
                for (j in i + 1 until boxes.size) {
                    val merged = tryMerging(boxes[i], boxes[j])
                    if (merged != null) {
                        boxes.removeAt(j)
                        boxes.removeAt(i)
                        boxes.add(merged)
                        continue@loop
                    }
                }
            }
            done = true
        }
        return boxes
    }

    private fun cutBoxes(boxes: MutableList<LodBlockBoundingBox>, cut: LodBlockBoundingBox): MutableList<LodBlockBoundingBox> {
        val box0BB = AABBi()
        val box1BB = AABBi()

        fun intersects(box0: LodBlockBoundingBox, box1: LodBlockBoundingBox): Boolean {
            box0BB.setMin(box0.minX.toInt(), box0.minY.toInt(), box0.minZ.toInt())
            box0BB.setMax(box0.maxX.toInt(), box0.maxY.toInt(), box0.maxZ.toInt())
            box1BB.setMin(box1.minX.toInt(), box1.minY.toInt(), box1.minZ.toInt())
            box1BB.setMax(box1.maxX.toInt(), box1.maxY.toInt(), box1.maxZ.toInt())
            return box0BB.intersectsAABB(box1BB)
        }

        fun cutBox(box: LodBlockBoundingBox, cut: LodBlockBoundingBox, dest: MutableList<LodBlockBoundingBox>) {
            // Make bottom-x box
            if (box.minX < cut.minX) {
                dest.add(Companion.createVSBoundingBox(box.minX, box.minY, box.minZ, (cut.minX - 1).toByte(), box.maxY, box.maxZ))
            }
            // Make top-x box
            if (box.maxX > cut.maxX) {
                dest.add(Companion.createVSBoundingBox((cut.maxX + 1).toByte(), box.minY, box.minZ, box.maxX, box.maxY, box.maxZ))
            }

            // All boxes generated from this point will get minX/maxX from [cut]

            // Make bottom-y box
            if (box.minY < cut.minY) {
                dest.add(Companion.createVSBoundingBox(cut.minX, box.minY, box.minZ, cut.maxX, (cut.minY - 1).toByte(), box.maxZ))
            }
            // Make top-y box
            if (box.maxY > cut.maxY) {
                dest.add(Companion.createVSBoundingBox(cut.minX, (cut.maxY + 1).toByte(), box.minZ, cut.maxX, box.maxY, box.maxZ))
            }

            // All boxes generated from this point will get minX/maxX/minY/maxY from [cut]
            // Make bottom-z box
            if (box.minZ < cut.minZ) {
                dest.add(Companion.createVSBoundingBox(cut.minX, cut.minY, box.minZ, cut.maxX, cut.maxY, (cut.minZ - 1).toByte()))
            }
            // Make top-z box
            if (box.maxZ > cut.maxZ) {
                dest.add(Companion.createVSBoundingBox(cut.minX, cut.minY, (cut.maxZ + 1).toByte(), cut.maxX, cut.maxY, box.maxZ))
            }
        }

        if (boxes.isEmpty()) return boxes

        var done = false
        loop@while (!done) {
            for (i in 0 until boxes.size) {
                val box = boxes[i]
                if (intersects(box, cut)) {
                    boxes.removeAt(i)
                    cutBox(box, cut, boxes)
                    continue@loop
                }
            }
            done = true
        }

        return boxes
    }

    private fun generateCollisionPointsForBoxes(boxes: List<LodBlockBoundingBox>): List<CollisionPoint> {
        // Near flat: 4 points
        // Near post: 4 points
        // Near cube: 1 points

        val collisionPoints = ArrayList<CollisionPoint>()
        for (box in boxes) {
            val xLen = box.maxX + 1 - box.minX
            val yLen = box.maxY + 1 - box.minY
            val zLen = box.maxZ + 1 - box.minZ

            if (xLen * 2 <= yLen && xLen * 2 <= zLen) {
                // flat like along X
                val radius = xLen / 2.0f
                val xPos = (box.maxX + 1 + box.minX) / 2.0f
                val minY = box.minY + radius
                val minZ = box.minZ + radius
                val maxY = box.maxY + 1 - radius
                val maxZ = box.maxZ + 1 - radius
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, minY / 16.0f, minZ / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, minY / 16.0f, maxZ / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, maxY / 16.0f, minZ / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, maxY / 16.0f, maxZ / 16.0f), radius / 16.0f))
            } else if (yLen * 2 <= xLen && yLen * 2 <= zLen) {
                // flat like along Y
                val radius = yLen / 2.0f
                val yPos = (box.maxY + 1 + box.minY) / 2.0f
                val minX = box.minX + radius
                val minZ = box.minZ + radius
                val maxX = box.maxX + 1 - radius
                val maxZ = box.maxZ + 1 - radius
                collisionPoints.add(CollisionPoint(Vector3f(minX / 16.0f, yPos / 16.0f, minZ / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(minX / 16.0f, yPos / 16.0f, maxZ / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(maxX / 16.0f, yPos / 16.0f, minZ / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(maxX / 16.0f, yPos / 16.0f, maxZ / 16.0f), radius / 16.0f))
            } else if (zLen * 2 <= xLen && zLen * 2 <= yLen) {
                // fence like along Z
                val radius = zLen / 2.0f
                val zPos = (box.maxZ + 1 + box.minZ) / 2.0f
                val minX = box.minX + radius
                val minY = box.minY + radius
                val maxX = box.maxX + 1 - radius
                val maxY = box.maxY + 1 - radius
                collisionPoints.add(CollisionPoint(Vector3f(minX / 16.0f, minY / 16.0f, zPos / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(minX / 16.0f, maxY / 16.0f, zPos / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(maxX / 16.0f, minY / 16.0f, zPos / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(maxX / 16.0f, maxY / 16.0f, zPos / 16.0f), radius / 16.0f))
            } else if (xLen >= 2 * yLen && xLen >= 2 * zLen) {
                // post like along X
                val radius = min(yLen, zLen) / 2.0f
                val yPos = (box.maxY + 1 + box.minY) / 2.0f
                val zPos = (box.maxZ + 1 + box.minZ) / 2.0f
                collisionPoints.add(CollisionPoint(Vector3f((box.minX + radius) / 16.0f, yPos / 16.0f, zPos / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(((box.maxX + 1 + box.minX) / 2.0f) / 16.0f, yPos / 16.0f, zPos / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f((box.maxX + 1 - radius) / 16.0f, yPos / 16.0f, zPos / 16.0f), radius / 16.0f))
            } else if (yLen >= 2 * xLen && yLen >= 2 * zLen) {
                // post like along Y
                val radius = min(xLen, zLen) / 2.0f
                val xPos = (box.maxX + 1 + box.minX) / 2.0f
                val zPos = (box.maxZ + 1 + box.minZ) / 2.0f
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, (box.minY + radius) / 16.0f, zPos / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, ((box.maxY + 1 + box.minY) / 2.0f) / 16.0f, zPos / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, (box.maxY + 1 - radius) / 16.0f, zPos / 16.0f), radius / 16.0f))
            } else if (zLen >= 2 * xLen && zLen >= 2 * yLen) {
                // post like along Z
                val radius = min(xLen, yLen) / 2.0f
                val xPos = (box.maxX + 1 + box.minX) / 2.0f
                val yPos = (box.maxY + 1 + box.minY) / 2.0f
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, yPos / 16.0f, (box.minZ + radius) / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, yPos / 16.0f, ((box.maxZ + 1 + box.minZ) / 2.0f) / 16.0f), radius / 16.0f))
                collisionPoints.add(CollisionPoint(Vector3f(xPos / 16.0f, yPos / 16.0f, (box.maxZ + 1 - radius) / 16.0f), radius / 16.0f))
            } else {
                // box
                val radius = min(xLen, min(yLen, zLen)) / 2.0f
                val centerX = (box.maxX + 1 + box.minX) / 2.0f
                val centerY = (box.maxY + 1 + box.minY) / 2.0f
                val centerZ = (box.maxZ + 1 + box.minZ) / 2.0f
                collisionPoints.add(CollisionPoint(Vector3f(centerX / 16.0f, centerY / 16.0f, centerZ / 16.0f), radius / 16.0f))
            }
        }
        return collisionPoints
    }

    private fun generateShapeFromVoxel(voxelShape: VoxelShape): Lod1SolidBoxesCollisionShape? {
        val posBoxes = ArrayList<LodBlockBoundingBox>()
        var failed = false
        var maxBoxesToTest = 20
        voxelShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
            if (failed) {
                return@forAllBoxes
            }
            val lodMinX = (minX * 16).roundToInt().toByte()
            val lodMinY = (minY * 16).roundToInt().toByte()
            val lodMinZ = (minZ * 16).roundToInt().toByte()
            val lodMaxX = ((maxX * 16).roundToInt() - 1).toByte()
            val lodMaxY = ((maxY * 16).roundToInt() - 1).toByte()
            val lodMaxZ = ((maxZ * 16).roundToInt() - 1).toByte()
            if (lodMinX !in 0..15 || lodMinY !in 0..15 || lodMinZ !in 0..15 || lodMaxX !in 0..15|| lodMaxY !in 0..15|| lodMaxZ !in 0..15) {
                // Out of range
                failed = true
                return@forAllBoxes
            } else {
                posBoxes.add(Companion.createVSBoundingBox(lodMinX, lodMinY, lodMinZ, lodMaxX, lodMaxY, lodMaxZ))
            }
            if (maxBoxesToTest == 0) {
                failed = true
            } else {
                maxBoxesToTest--
            }
        }

        if (failed) {
            return null
        }

        if (posBoxes.isEmpty()) {
            // No boxes? It's the empty shape
            return Lod1SolidBoxesCollisionShape(Companion.createEmptyVSBoundingBox(), emptyList(), emptyList(), listOf(Companion.createFullVSBoundingBox()))
        }

        mergeBoxes(posBoxes)

        if (posBoxes.size > MAX_BOXES) {
            return null
        }

        var negBoxes = mutableListOf(Companion.createFullVSBoundingBox())
        for (posBox in posBoxes) {
            negBoxes = cutBoxes(negBoxes, posBox)
        }

        mergeBoxes(negBoxes)

        if (negBoxes.size > MAX_BOXES) {
            return null
        }

        val collisionPoints = generateCollisionPointsForBoxes(posBoxes)

        if (collisionPoints.size > MAX_POINTS) {
            return null
        }

        val minTotalAABB = Vector3i(posBoxes[0].minX.toInt(), posBoxes[0].minY.toInt(), posBoxes[0].minZ.toInt())
        val maxTotalAABB = Vector3i(posBoxes[0].maxX.toInt(), posBoxes[0].maxY.toInt(), posBoxes[0].maxZ.toInt())
        for (i in 1 until posBoxes.size) {
            minTotalAABB.x = min(minTotalAABB.x, posBoxes[i].minX.toInt())
            minTotalAABB.y = min(minTotalAABB.y, posBoxes[i].minY.toInt())
            minTotalAABB.z = min(minTotalAABB.z, posBoxes[i].minZ.toInt())
            maxTotalAABB.x = max(maxTotalAABB.x, posBoxes[i].maxX.toInt())
            maxTotalAABB.y = max(maxTotalAABB.y, posBoxes[i].maxY.toInt())
            maxTotalAABB.z = max(maxTotalAABB.z, posBoxes[i].maxZ.toInt())
        }
        val overallBox = LodBlockBoundingBox.createVSBoundingBox(
            minTotalAABB.x.toByte(), minTotalAABB.y.toByte(), minTotalAABB.z.toByte(), maxTotalAABB.x.toByte(),
            maxTotalAABB.y.toByte(), maxTotalAABB.z.toByte()
        )

        return Lod1SolidBoxesCollisionShape(
            overallBox,
            collisionPoints,
            posBoxes,
            negBoxes,
        )
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
            val collisionShape = Lod1SolidBoxesCollisionShape(
                overallBoundingBox = overallBox,
                collisionPoints = points,
                solidBoxes = mergeBoxes(positiveBoxes),
                negativeBoxes = mergeBoxes(negativeBoxes),
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

        val generatedCollisionShapesMap = HashMap<VoxelShape, Lod1SolidCollisionShape?>()
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
                    } else if (generatedCollisionShapesMap.contains(voxelShape)) {
                        if (generatedCollisionShapesMap[voxelShape] != null) {
                            generatedCollisionShapesMap[voxelShape]!!
                        } else {
                            fullBlockCollisionShape
                        }
                    } else {
                        val generated = generateShapeFromVoxel(voxelShape)
                        generatedCollisionShapesMap[voxelShape] = generated
                        generated ?: fullBlockCollisionShape
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
