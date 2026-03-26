package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
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
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3d
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.BoxesBlockShape
import org.valkyrienskies.core.api.physics.blockstates.CollisionPoint
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.api_impl.events.RegisterBlockStateEventImpl
import org.valkyrienskies.mod.common.BlockStateInfoProvider
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.accessors.world.level.block.SlabBlockAccessor
import org.valkyrienskies.mod.mixin.accessors.world.level.block.StairBlockAccessor
import org.valkyrienskies.mod.util.logger
import java.util.Optional
import kotlin.math.roundToInt

private data class VSBlockStateInfo(
    val id: ResourceLocation,
    val priority: Int,
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
    val type: VsiBlockType?,
    val noCollisionOverride: Boolean?,
)

object MassDatapackResolver : BlockStateInfoProvider {
    private val map = hashMapOf<ResourceLocation, VSBlockStateInfo>()
    private val mcBlockStateToVs: MutableMap<BlockState, VsiBlockState> = HashMap()

    val blockStateData: Collection<VsiBlockState> = mcBlockStateToVs.values

    val loader get() = VSMassDataLoader()

    override val priority: Int
        get() = 100

    override fun getBlockStateMass(blockState: BlockState): Double? =
        map[BuiltInRegistries.BLOCK.getKey(blockState.block)]?.mass

    override fun getBlockStateType(blockState: BlockState): VsiBlockType? {
        val vsState = mcBlockStateToVs[blockState] ?: return null
        return vsCore.blockTypes.getType(vsState)
    }

    var registeredBlocks = false
        private set

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
                        BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, tagInfo.id))
                    if (tag != null) {

                        if (!tag.isPresent) {
                            logger.warn("No specified tag '${tagInfo.id}' doesn't exist!")
                            return@forEach
                        }

                        tag.get().forEach {
                            add(
                                VSBlockStateInfo(
                                    BuiltInRegistries.BLOCK.getKey(it.value()), tagInfo.priority, tagInfo.mass, tagInfo.friction,
                                    tagInfo.elasticity, tagInfo.type, tagInfo.noCollisionOverride
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
            val friction = element.asJsonObject["friction"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockFriction
            val elasticity = element.asJsonObject["elasticity"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockElasticity

            val priority = element.asJsonObject["priority"]?.asInt ?: decideDefaultPriority(origin)

            val overrideNoCollision = element.asJsonObject["no_collision"]?.asBoolean

            if (tag != null) {
                addToBeAddedTags(VSBlockStateInfo(ResourceLocation(tag), priority, weight, friction, elasticity, null, overrideNoCollision))
            } else {
                val block = element.asJsonObject["block"]?.asString
                    ?: throw IllegalArgumentException("No block or tag in file $origin")

                add(VSBlockStateInfo(ResourceLocation(block), priority, weight, friction, elasticity, null, overrideNoCollision))
            }
        }
    }

    fun decideDefaultPriority(resourceLocation: ResourceLocation) = when {
        resourceLocation.namespace.equals(ValkyrienSkiesMod.MOD_ID) -> 50
        resourceLocation.namespace.equals("custom") -> 1000
        else -> 100
    }

    /**
     * This is mostly an internal function, not meant for public use.
     * If however, you absolutely _need_ it to generate some stair collision shapes,
     * then it is left public.
     *
     * @see generateShapeFromVoxel
     */
    @JvmStatic
    fun generateStairCollisionShapes(stairShapes: Array<VoxelShape>): Map<VoxelShape, SolidBlockShape> {
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
        stairShapes.forEach { stairShape ->
            val points: MutableList<CollisionPoint> = ArrayList()
            val positiveBoxes: MutableList<AABBic> = ArrayList()
            val negativeBoxes: MutableList<AABBic> = ArrayList()

            testPoints.forEachIndexed { index, testPoint ->
                var added = false
                stairShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
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

            map[stairShape] = collisionShape
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

    /**
     * This is left public so it can be used in [org.valkyrienskies.mod.mixin.server.MixinMinecraftServer].
     *
     * It is **not recommended** to call this yourself!
     */
    fun registerAllBlockStates(blockStates: Iterable<BlockState>) {
        val fullLodBoundingBox = AABBi(0, 0, 0, 15, 15, 15)
        val fullBlockCollisionPoints = listOf(
            CollisionPoint(.25f, .25f, .25f, .25f),
            CollisionPoint(.25f, .25f, .75f, .25f),
            CollisionPoint(.25f, .75f, .25f, .25f),
            CollisionPoint(.25f, .75f, .75f, .25f),
            CollisionPoint(.75f, .25f, .25f, .25f),
            CollisionPoint(.75f, .25f, .75f, .25f),
            CollisionPoint(.75f, .75f, .25f, .25f),
            CollisionPoint(.75f, .75f, .75f, .25f),
        )
        val fullBlockCollisionShape = vsCore.newSolidStateBoxesShapeBuilder()
            .addCollisionPoints(fullBlockCollisionPoints)
            .addPositiveBox(fullLodBoundingBox)
            .build()

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

        val generatedCollisionShapesMap = HashMap<VoxelShape, SolidBlockShape?>()
        val liquidMaterialToDensityMap: HashMap<Fluid, Pair<Double, Double>> = hashMapOf(Fluids.WATER to Pair(1000.0, 0.3), Fluids.LAVA to Pair(10000.0, 1.0), Fluids.FLOWING_WATER to Pair(1000.0, 0.3), Fluids.FLOWING_LAVA to Pair(10000.0, 1.0))

        val fluidStateToBlockTypeMap = HashMap<FluidState, LiquidState>()

        // Get the id of the fluid state/create a new fluid state if necessary
        fun getFluidState(fluidState: FluidState, blockStateInfo: VSBlockStateInfo?, isLiquid: Boolean = false): LiquidState {
            val cached = fluidStateToBlockTypeMap[fluidState]
            if (cached != null) return cached
            // Treat source fluids as full blocks in VS physics registration.
            val maxY = if (fluidState.isSource) {
                15
            } else {
                ((fluidState.ownHeight * 16.0).roundToInt() - 1).coerceIn(0, 15)
            }
            val fluidBox = AABBi(0, 0, 0, 15, maxY, 15)
            return if (fluidState.type in liquidMaterialToDensityMap) {
                val (density, dragCoefficient) = liquidMaterialToDensityMap[fluidState.type]!!
                val newFluidBlockState = vsCore.newLiquidStateBuilder()
                    .boxShape(fluidBox)
                    .density(density)
                    .dragCoefficient(dragCoefficient)
                    .velocity(Vector3d())
                    .build()

                newFluidBlockState
            } else {
                if (isLiquid) {
                    val density = blockStateInfo?.mass ?: VSGameConfig.SERVER.defaultBlockMass
                    val dragCoefficient = blockStateInfo?.friction ?: VSGameConfig.SERVER.defaultBlockFriction
                    val newFluidBlockState = vsCore.newLiquidStateBuilder()
                        .boxShape(fluidBox)
                        .density(density)
                        .dragCoefficient(dragCoefficient)
                        .velocity(Vector3d())
                        .build()

                    liquidMaterialToDensityMap[fluidState.type] = Pair(density, dragCoefficient)
                    newFluidBlockState
                } else {
                    //default
                    val newFluidBlockState = vsCore.newLiquidStateBuilder()
                        .boxShape(fluidBox)
                        .density(VSGameConfig.SERVER.defaultBlockMass)
                        .dragCoefficient(liquidMaterialToDensityMap[Fluids.WATER]!!.second)
                        .velocity(Vector3d())
                        .build()
                    newFluidBlockState
                }
            }
        }


        blockStates.forEach { blockState: BlockState ->
            val vsBlockState: VsiBlockState
            if (blockState.isAir) {
                vsBlockState = vsCore.blockTypes.airState
            } else {
                vsBlockState = if (blockState.liquid()) { //TODO: This is also deprecated. I could check if the blockState is wet and not waterlogged but couldn't be sure if that's what this is for.
                    VsiBlockState(null, getFluidState(blockState.fluidState, map[BuiltInRegistries.BLOCK.getKey(blockState.block)], true))
                } else {
                    val voxelShape: VoxelShape
                    if (blockState.isSolid) {
                        voxelShape = blockState.getShape(dummyBlockGetter, BlockPos.ZERO)
                    } else {
                        voxelShape = blockState.getCollisionShape(dummyBlockGetter, BlockPos.ZERO)
                    }

                    var collisionShape: SolidBlockShape = if (voxelShapeToCollisionShapeMap.contains(voxelShape)) {
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

                    val vsBlockStateInfo = map[BuiltInRegistries.BLOCK.getKey(blockState.block)]

                    // If overrideNoCollision is set to true in datapack, force it to have no collision shape
                    if (vsBlockStateInfo?.noCollisionOverride ?: false) {
                        // Won't ever be null with an empty list
                        collisionShape = vsCore.solidShapeUtils.generateShapeFromBoxes(mutableListOf())!!
                    }

                    // Create new solid block state
                    var solidState = vsCore.newSolidStateBuilder()
                        .shape(collisionShape)
                        .elasticity(vsBlockStateInfo?.elasticity ?: VSGameConfig.SERVER.defaultBlockElasticity)
                        .friction(vsBlockStateInfo?.friction ?: VSGameConfig.SERVER.defaultBlockFriction)
                        .hardness(VSGameConfig.SERVER.defaultBlockHardness) // Unused for now, placeholder for later
                        .build()

                    val fluidState = if (!blockState.fluidState.isEmpty) {
                        getFluidState(blockState.fluidState, null)
                    } else {
                        null
                    }

                    VsiBlockState(solidState, fluidState)

                }
            }
            mcBlockStateToVs[blockState] = vsBlockState
        }

        runRegisterBlockStateEvent()
        registeredBlocks = true
    }

    private fun runRegisterBlockStateEvent() {
        val event = RegisterBlockStateEventImpl()
        ValkyrienSkiesMod.api.registerBlockStateEvent.emit(event)
        mcBlockStateToVs.putAll(event.toRegister)
    }

    private val logger by logger()

}
