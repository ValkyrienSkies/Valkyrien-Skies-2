package org.valkyrienskies.mod.common.blockstate

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.tags.TagKey
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.shapes.VoxelShape
import org.jetbrains.annotations.ApiStatus.Internal
import org.joml.Vector3d
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.networking.PacketSyncBlockStateProperties
import org.valkyrienskies.mod.common.util.BlockShapeUtil
import org.valkyrienskies.mod.common.util.BlockShapeUtil.toAABBi
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.util.logger
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Pattern
import kotlin.system.measureNanoTime

data class VSBlockProperties(
    val solid: SolidProperties? = null,
    val medium: MediumProperties? = null,
    val displacement: AABBic? = null
)

data class VSFluidProperties(
    val density: Double,
    val dragCoefficient: Double,
    val velocity: Vector3d,
    val shapeOverride: AABBic? = null
)

class NonNullMap<K, V>(private val map: Map<K, V>) {
    operator fun get(key: K): V = map[key]!!
}
/**
 * mass datapack resolver 2: electric boogaloo
 */
object BlockStateInfoResolver {
    private val liquidIdToFlowingFluid: MutableMap<Int, FlowingFluid?> = HashMap()

    private val blockTags: MutableMap<ResourceLocation, BlockTagProperties> = HashMap()
    private val fluidTags: MutableMap<ResourceLocation, FluidTagProperties> = HashMap()
    private val pendingBlocks: MutableMap<ResourceLocation, MutableMap<String, PendingBlockProperties>> = HashMap()
    private val pendingFluids: MutableMap<ResourceLocation, MutableMap<String, PendingFluidProperties>> = HashMap()
    private val blocks: MutableMap<ResourceLocation, MutableMap<String, VSBlockProperties>> = HashMap()
    private val fluids: MutableMap<ResourceLocation, MutableMap<String, VSFluidProperties>> = HashMap()

    /**
     * Values of this map should never be null, as we iterate through every single block state in [registerAllBlockStates]
     * and thus we have a corresponding VsiBlockState for every BlockState.
     * If a value _is_ null, something has gone very wrong.
     */
    private val mcState2VsState: MutableMap<BlockState, VsiBlockState> = HashMap()

    /**
     * Values of this map should never be null, as we iterate through every single block state in [registerAllBlockStates]
     * and thus we have a corresponding VsiBlockState for every BlockState.
     * If a value _is_ null, something has gone very wrong.
     */
    val blockStateToVs = NonNullMap(mcState2VsState)

    init {
        VSGameEvents.tagsAreLoaded.on { _, listener ->
            loadTags()
            listener.unregister()
        }
    }

    /*
    TODO #1
    take liquid properties out of state properties and put it in its own map so that we look up separate properties for
    the solid and liquid as opposed to taking the liquid from the full state (as this will return null) and thus we
    end up putting the default liquid state for blocks like waterlogged slabs
     */

    /*
    TODO #2
    allow tags to do blockstates, because yes
     */

    /*
    TODO #3
    new blockstate format where instead of matching the string, we hae a base value + each property
    has a modifier for its values, and we register the mass by getting the modifier for each property and summing them up for the full value
     */

    /*
    TODO #4
    define property templates in a templates.json file in the vs_mass directory
    these would take a set amount of values and basically spit out properties
    for example, we could have a slab template that would take one value (likely the mass of its full block counterpart)
    and spit out properties that would basically just be half the given mass, or the full given mass if double slab

    would help for saving time when defining entries for stuff like slabs, stairs etc
    instead of writing out a whole entry just smthn like:

    "block": "minecraft:oak_stairs",
    "template": {
        "name": "valkyrienskies:slab",
        "input": "minecraft:oak_planks <- this value would get resolved into the actual mass
    }

    also for defining templates, they would just have a name like "slab" then we get the namespace from the namespace
    of the templates.json file they're placed in
    having users define templates in a templates.json file in the vs_mass directory means we can filter the map of jsons for the
    ones that have a path of "template.json", and make sure we load the templates before we start loading any properties
     */

    fun getLiquidStateId(blockState: BlockState): Int? =
        blockState.vsType.let(vsCore.blockTypes::getLiquidStateId)

    fun getFlowingFluid(liquidStateId: Int): FlowingFluid? {
        if (liquidIdToFlowingFluid.containsKey(liquidStateId)) {
            return liquidIdToFlowingFluid[liquidStateId]
        }
        val fluid = mcState2VsState.entries.firstNotNullOfOrNull { (blockState, vsState) ->
            val blockType =
                vsCore.blockTypes.getType(vsState) ?: return@firstNotNullOfOrNull null
            if (vsCore.blockTypes.getLiquidStateId(blockType) == liquidStateId) {
                blockState.fluidState.type as? FlowingFluid
            } else {
                null
            }
        }
        liquidIdToFlowingFluid[liquidStateId] = fluid
        return fluid
    }

    val blockStateData: Collection<VsiBlockState> = mcState2VsState.values

    var hasRegistered = false
        private set

    val loader get() = BlockStateInfoDataLoader()

    fun getVsiBlockState(blockState: BlockState): VsiBlockState {
        return mcState2VsState[blockState]!!
    }

    fun getVsiBlockType(blockState: BlockState): VsiBlockType {
        return vsCore.blockTypes.getType(getVsiBlockState(blockState))!!
    }


    fun getVSProperties(blockState: BlockState): VSBlockProperties? {
        val string = blockState.idAndProperties()
        return blocks[string.a]?.getOrOther(string.b, "default")
    }

    fun getVSProperties(fluidState: FluidState): VSFluidProperties? {
        val string = fluidState.idAndProperties()
        return fluids[string.a]?.getOrOther(string.b, "default")
    }

    /**
     * This is left public so that it can be called in [org.valkyrienskies.mod.mixin.server.MixinMinecraftServer]
     * **Do not call this method otherwise!**
     */
    @Internal
    fun registerAllBlockStates(blockStates: List<BlockState>) {
        logger.info("Registering all mc states (${blockStates.size}). We have ${blocks.size + fluids.size} properties loaded from data.")
        val voxelShapeToSolidShape: MutableMap<VoxelShape, SolidBlockShape> = HashMap(BlockShapeUtil.generateCommonShapes())

        fun generateShape(voxelShape: VoxelShape): SolidBlockShape =
            if (voxelShapeToSolidShape.contains(voxelShape)) {
                voxelShapeToSolidShape[voxelShape]!!
            } else {
                val generatedShape = BlockShapeUtil.generateShapeFromVoxel(voxelShape)
                if (generatedShape != null) {
                    voxelShapeToSolidShape[voxelShape] = generatedShape
                    generatedShape
                } else {
                    BlockShapeUtil.fullBlockCollisionShape
                }
            }
        fun buildDefaultSolidState(voxelShape: VoxelShape) =
            vsCore.newSolidStateBuilder()
                .shape(generateShape(voxelShape))
                .mass(VSGameConfig.SERVER.blockProperties.defaultBlockMass)
                .friction(VSGameConfig.SERVER.blockProperties.defaultBlockFriction)
                .elasticity(VSGameConfig.SERVER.blockProperties.defaultBlockElasticity)
                .hardness(VSGameConfig.SERVER.blockProperties.defaultBlockHardness)
                .build()
        fun buildDefaultLiquidState(shape: AABBic) =
            vsCore.newLiquidStateBuilder()
                .boxShape(shape)
                .density(VSGameConfig.SERVER.blockProperties.defaultLiquidDensity)
                .dragCoefficient(VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient)
                .velocity(Vector3d())
                .build()

        fun getSolidState(props: VSBlockProperties?, voxelShape: VoxelShape) = if (props?.solid != null) {
            val shape: SolidBlockShape =
                if (props.solid.noCollision) BlockShapeUtil.noCollisionShape
                else if (props.solid.shapeOverride != null)
                    vsCore.solidShapeUtils.generateShapeFromBoxes(mutableListOf(props.solid.shapeOverride)) ?: generateShape(voxelShape)
                else generateShape(voxelShape)

            vsCore.newSolidStateBuilder()
                .shape(shape)
                .mass(props.solid.mass)
                .friction(props.solid.friction)
                .elasticity(props.solid.elasticity)
                .hardness(props.solid.hardness)
                .build()
        } else buildDefaultSolidState(voxelShape)

        fun getLiquidState(props: VSFluidProperties?, voxelShape: VoxelShape) = if (props != null) {
            val shape: AABBic = props.shapeOverride ?: voxelShape.toAABBi()

            vsCore.newLiquidStateBuilder()
                .boxShape(shape)
                .density(props.density)
                .dragCoefficient(props.dragCoefficient)
                .velocity(props.velocity)
                .build()
        } else buildDefaultLiquidState(voxelShape.toAABBi())

        blockStates.forEach { blockState ->
            val composition: Composition = getComposition(blockState)
            val vsiBlockState: VsiBlockState
            when (composition) {
                Composition.AIR -> {
                    vsiBlockState = vsCore.blockTypes.airState
                }
                Composition.SOLID, Composition.EMPTY -> {
                    val voxelShape = BlockShapeUtil.getShapeForVS(blockState)
                    val props = getVSProperties(blockState)

                    val mediumState = if (props?.medium != null)
                        buildMediumState(props.medium.dragCoefficient, props.medium.shape ?: BlockShapeUtil.fullLodBoundingBox)
                    else null

                    vsiBlockState = VsiBlockState(getSolidState(props, voxelShape), mediumState)
                }
                Composition.MIXED -> {
                    val voxelShape = BlockShapeUtil.getShapeForVS(blockState)
                    val props = getVSProperties(blockState)
                    vsiBlockState = VsiBlockState(getSolidState(props, voxelShape), getLiquidState(getVSProperties(blockState.fluidState), BlockShapeUtil.getFluidShape(blockState.fluidState)))
                }
                Composition.LIQUID -> {
                    val props = getVSProperties(blockState.fluidState)
                    vsiBlockState = VsiBlockState(null, getLiquidState(props, BlockShapeUtil.getFluidShape(blockState.fluidState)))
                }
            }
            mcState2VsState[blockState] = vsiBlockState
        }
        logger.info("Registered ${mcState2VsState.size} states for vs. This number should be the same as the number of mc states.")

        hasRegistered = true
    }

    fun syncBlockStates(player: MinecraftPlayer) {
        logger.info("Syncing blockstates to ${player.uuid}")
        with(vsCore.simplePacketNetworking) {
            val blockMap = blocks.mapKeys { it.key.toString() }
            val fluidMap = fluids.mapKeys { it.key.toString() }
            PacketSyncBlockStateProperties(blockMap, fluidMap).sendToClient(player)
        }
    }

    fun clearBlockStates(player: MinecraftPlayer) {
        logger.info("Clearing synced blockstates from ${player.uuid}")
        with(vsCore.simplePacketNetworking) {
            PacketSyncBlockStateProperties().sendToClient(player)
        }
    }

    fun loadTags() {
        logger.info("Loading tag entries.")
        var tags = 0
        var entries = 0
        blockTags.forEach { (tagId, tagProperties) ->
            val tag = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, tagId))

            if (tag != null) {
                if (!tag.isPresent) {
                    if (tagId.namespace == "minecraft") {
                        logger.warn("Block tag '$tagId' does not exist!")
                    }
                    return@forEach
                }
            }

            tags++

            tag.get().forEach {
                val id = BuiltInRegistries.BLOCK.getKey(it.value())
                if (!tagProperties.exclusions.contains(id)) {
                    entries++
                    putBlockProperties(id.toString(), "default", tagProperties.properties)
                }
            }
        }

        logger.info("Loaded $tags block tag entries (properties for $entries blocks).")
        tags = 0
        entries = 0

        fluidTags.forEach { (tagId, tagProperties) ->
            val tag = BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, tagId))

            if (tag != null) {
                if (!tag.isPresent) {
                    if (tagId.namespace == "minecraft") {
                        logger.warn("Fluid tag '$tagId' does not exist!")
                    }
                    return@forEach
                }
            }

            tags++

            tag.get().forEach {
                val id = BuiltInRegistries.FLUID.getKey(it.value())
                if (!tagProperties.exclusions.contains(id)) {
                    entries++
                    putFluidProperties(id.toString(), "default", tagProperties.properties)
                }
            }
        }

        logger.info("Loaded $tags fluid tag entries (properties for $entries fluids).")
        resolveAll()
    }

    // region resolve pending values -> actual values
    /**
     * Resolve all values.
     * **This can take extremely long if there are deep dependency chains!**
     */
    private fun resolveAll() {
        if (pendingBlocks.isEmpty() && pendingFluids.isEmpty()) return
        logger.info("Resolving all values (${pendingBlocks.size} blocks, ${pendingFluids.size} fluids). This may take a while if there are a large amount of dependent values!")

        fun <T, P> resolve(resolvedMap: MutableMap<ResourceLocation, MutableMap<String, T>>, pendingMap: MutableMap<ResourceLocation, MutableMap<String, P>>, type: String, resolver: (P, Boolean) -> T?) {
            fun put(id: ResourceLocation, key: String, propertiesToPut: T) {
                resolvedMap.computeIfAbsent(id) { mutableMapOf() }[key] = propertiesToPut
            }
            var unresolvedCount = 0
            val nanos = measureNanoTime {
                resolvedMap.clear()

                var unresolved = pendingMap.flatMap { (id, states) ->
                    states.map { (state, props) -> Triple(id, state, props) }
                }

                var progress = true
                while (unresolved.isNotEmpty() && progress) {
                    progress = false
                    val stillUnresolved = mutableListOf<Triple<ResourceLocation, String, P>>()

                    for ((id, state, pending) in unresolved) {
                        val resolved = resolver(pending, false)
                        if (resolved != null) {
                            put(id, state, resolved)
                            progress = true
                        } else {
                            stillUnresolved.add(Triple(id, state, pending))
                        }
                    }
                    unresolved = stillUnresolved
                }

                unresolved.forEach { (id, state, pending) ->
                    logger.error("couldn't fully resolve dependent values for $type $id[$state] due to a circular or missing reference, falling back to defaults for unresolved fields.")
                    put(id, state, resolver(pending, true)!!) // force guarantees a non-null value
                }

                unresolvedCount = unresolved.size
                pendingMap.clear()
            }

            logger.info("Resolving all $type values took %.3f seconds (%d unresolved).".format(nanos / 1_000_000_000.0, unresolvedCount))
        }

        resolve(blocks, pendingBlocks, "block") { pending, force -> resolveBlock(pending, force) }
        resolve(fluids, pendingFluids, "fluid") { pending, force -> resolveFluid(pending, force) }
    }

    private fun resolveBlock(pending: PendingBlockProperties, force: Boolean): VSBlockProperties? {
        val solid = pending.solid?.let { resolveSolid(it, force) ?: return null }
        val medium = pending.medium?.let { resolveMedium(it, force) ?: return null }
        return VSBlockProperties(solid, medium, pending.displacement)
    }

    private fun resolveSolid(p: PendingSolidProperties, force: Boolean): SolidProperties? {
        val mass = resolveValue(p.mass, VSGameConfig.SERVER.blockProperties.defaultBlockMass, force, blocks) { it.solid?.mass } ?: return null
        val friction = resolveValue(p.friction, VSGameConfig.SERVER.blockProperties.defaultBlockFriction, force, blocks) { it.solid?.friction } ?: return null
        val elasticity = resolveValue(p.elasticity, VSGameConfig.SERVER.blockProperties.defaultBlockElasticity, force, blocks) { it.solid?.elasticity } ?: return null
        val hardness = resolveValue(p.hardness, VSGameConfig.SERVER.blockProperties.defaultBlockHardness, force,blocks) { it.solid?.hardness } ?: return null
        return SolidProperties(mass, friction, elasticity, hardness, p.noCollision, p.shapeOverride)
    }

    private fun resolveMedium(p: PendingMediumProperties, force: Boolean): MediumProperties? {
        val drag = resolveValue(p.dragCoefficient, VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient, force, blocks) { it.medium?.dragCoefficient } ?: return null
        return MediumProperties(drag, p.shape)
    }

    private fun resolveFluid(p: PendingFluidProperties, force: Boolean): VSFluidProperties? {
        val density = resolveValue(p.properties.density, VSGameConfig.SERVER.blockProperties.defaultLiquidDensity, force, fluids) { it.density } ?: return null
        val drag = resolveValue(p.properties.dragCoefficient, VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient, force, fluids) { it.dragCoefficient } ?: return null
        return VSFluidProperties(density, drag, p.properties.velocity, p.properties.shapeOverride)
    }

    private fun <T : Any> resolveValue(value: NumericValue, default: Double, force: Boolean, map: Map<ResourceLocation, Map<String, T>>, extractor: (T) -> Double?): Double? {
        return when (value) {
            is NumericValue.Literal -> value.value
            is NumericValue.Default -> value.mult * default
            is NumericValue.Dependent -> {
                val target = map[value.targetId]?.getOrOther(value.targetState, "default")
                val resolved = target?.let { extractor(it) }?.let { it * value.mult }
                when {
                    resolved != null -> resolved
                    force -> {
                        logger.error("could not resolve dependent value pointing at ${value.targetId}[${value.targetState}], using default $default.")
                        default
                    }
                    else -> null
                }
            }
        }
    }
    // endregion

    // region utility functions
    private fun putBlockProperties(id: String, key: String, propertiesToPut: PendingBlockProperties) {
        pendingBlocks.computeIfAbsent(ResourceLocation(id)) { mutableMapOf() }.compute(key) { _, properties ->
            priorityPut(properties, propertiesToPut) as PendingBlockProperties
        }
    }

    private fun putFluidProperties(id: String, key: String, propertiesToPut: PendingFluidProperties) {
        pendingFluids.computeIfAbsent(ResourceLocation(id)) { mutableMapOf() }.compute(key) { _, properties ->
            priorityPut(properties, propertiesToPut) as PendingFluidProperties
        }
    }

    private fun putBlockTag(id: String, propertiesToPut: BlockTagProperties) {
        blockTags.compute(ResourceLocation(id)) { _, properties ->
            priorityPut(properties, propertiesToPut) as BlockTagProperties
        }
    }

    private fun putFluidTag(id: String, propertiesToPut: FluidTagProperties) {
        fluidTags.compute(ResourceLocation(id)) { _, properties ->
            priorityPut(properties, propertiesToPut) as FluidTagProperties
        }
    }

    private val priorityPut: (Supplier<Int>?, Supplier<Int>) -> Supplier<Int> = { properties, propertiesToPut ->
        if (properties == null) propertiesToPut
        else if (propertiesToPut.get() > properties.get()) propertiesToPut
        else properties
    }
    // endregion

    class BlockStateInfoDataLoader : SimpleJsonResourceReloadListener(Gson(), "vs_mass") {
        override fun apply(objects: MutableMap<ResourceLocation, JsonElement>, resourceManager: ResourceManager, profilerFiller: ProfilerFiller) {
            //val templates = objects.filter { it.key.path == "templates" }
            objects.forEach { (location, element) ->
                try {
                    if (element.isJsonArray) {
                        var i = 0
                        element.asJsonArray.forEach { element1: JsonElement ->
                            parse(element1, location, i)
                            i++
                        }
                    } else if (element.isJsonObject) {
                        parse(element, location)
                    } else throw IllegalArgumentException()
                } catch (e: Exception) {
                    logger.error(e)
                }
            }
        }

        class MassJsonParseException(override val message: String, val id: String? = null) : Exception(message) {
            fun getParseError(): String {
                return if (id == null) {
                    message
                } else {
                    "Parsing exception for $id: $message"
                }
            }
        }

        // region enums n stuff
        /**
         * The type of object a property entry applies to.
         */
        enum class IdType (val string: String) {
            BLOCK("block"),
            FLUID("fluid"),
            BLOCK_TAG("tag"),
            FLUID_TAG("fluid_tag"),
            NONE("");

            fun getId(jsonObject: JsonObject): String {
                return jsonObject.get(string).asString
            }
        }

        /**
         * The structure of a property entry. Used to determine how entries should be parsed.
         */
        enum class StructureType {
            BLOCK_BASIC,
            BLOCK_COMPOUND,
            BLOCK_STATES,
            BLOCK_STATES_MODIFIER,
            FLUID_BASIC,
            FLUID_STATES,
            BLOCK_TAG_BASIC,
            BLOCK_TAG_COMPOUND,
            BLOCK_TAG_STATES,
            FLUID_TAG_BASIC
            ;

            fun toStructure(): Structure {
                return Structure(this)
            }
        }

        /**
         * Represents the structure of a block/fluid property entry.
         *
         * @param type The [StructureType] of this Structure.
         * @param warn If this structure is still valid but has a warning, this is the message.
         * @param state2StructureType If this structure's type is [StructureType.BLOCK_STATES] or
         * [StructureType.FLUID_STATES], this is a map of each state to its structure, allowing for differing structures of states if needed.
         */
        class Structure(val type: StructureType, val warn: String? = null, val state2StructureType: Map<String, StructureType>? = null) {
            fun isWarn(): Boolean {
                return warn != null
            }

            companion object {
                /**
                 * Indicates that a problem has occurred with the entry, but it should be safe to parse.
                 * This means that when we return the warning, we should also have **fixed the problem**.
                 */
                fun warn(type: StructureType, string: String): Structure {
                    return Structure(type, warn = string)
                }
            }
        }

        fun decideDefaultPriority(resourceLocation: ResourceLocation) = when {
            resourceLocation.namespace.equals(ValkyrienSkiesMod.MOD_ID) -> 50
            resourceLocation.namespace.equals("custom") -> 1000
            else -> 100
        }

        // matches if the string is formatted like "key=value,key2=value2" etc
        val stateRegex: Predicate<String> = Pattern.compile("^(\\w+=[^,=]+(,\\w+=[^,=]+)*)$").asPredicate()

        val blockValues = listOf("mass", "friction", "elasticity", "hardness", "no_collision", "shape_override")
        val mediumValues = listOf("drag", "shape")
        val fluidValues = listOf("density", "drag", "velocity", "no_collision", "shape_override")
        // displacement state values are just "shape"
        // endregion

        /**
         * Determine the [IdType] for a property entry
         */
        private fun determineId(json: JsonObject): IdType {
            return when {
                json.has("block") -> IdType.BLOCK
                json.has("fluid") -> IdType.FLUID
                json.has("tag") || json.has("block_tag") -> IdType.BLOCK_TAG
                json.has("fluid_tag") -> IdType.FLUID_TAG
                else -> IdType.NONE
            }
        }

        /**
         * Determine the [Structure] of a property entry.
         */
        private fun determineStructure(json: JsonObject, idType: IdType, id: String): Structure {
            fun determineCompoundBlockStructure(json: JsonObject, id: String, tag: Boolean = false): Structure {
                val hasSolid = json.has("solid")
                val hasMedium = json.has("medium")

                return if (hasSolid && !hasMedium) {
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)

                    if (solidValid) {
                        if (tag) StructureType.BLOCK_TAG_COMPOUND.toStructure() else StructureType.BLOCK_COMPOUND.toStructure()
                    } else throw MassJsonParseException("Solid state is invalid!", id) // prevent so we default
                } else if (hasMedium && !hasSolid) {
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (mediumValid) {
                        if (tag) StructureType.BLOCK_TAG_COMPOUND.toStructure() else StructureType.BLOCK_COMPOUND.toStructure()
                    } else throw MassJsonParseException("Medium state is invalid!", id) // prevent so we default
                } else if (!hasMedium) { // we know that hasSolid is false here already so we don't need to check
                    throw MassJsonParseException("Json does not have a solid or medium state!", id)
                } else { // has both
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (solidValid && mediumValid) {
                        if (tag) StructureType.BLOCK_TAG_COMPOUND.toStructure() else StructureType.BLOCK_COMPOUND.toStructure()
                    } else if (!solidValid && !mediumValid) { // neither valid
                        throw MassJsonParseException("Neither medium or solid state is valid!", id)
                    } else if (!solidValid) { // medium is valid but solid isn't
                        // solid states are kinda more important so if this is invalid we should just completely error instead of keeping the medium state.
                        throw MassJsonParseException("Medium state is valid, but solid state is invalid!", id)
                    } else {
                        json.remove("medium")
                        Structure.warn(if (tag) StructureType.BLOCK_TAG_COMPOUND else StructureType.BLOCK_COMPOUND, "Medium state in block $id is invalid, but solid state is.")
                    }
                }
            }

            // some utility stuff for determining the main structure of the entry
            fun JsonObject.basic(block: Boolean = true): Boolean = if (block) this.hasAny(blockValues) else this.hasAny(fluidValues)
            fun JsonObject.compound(): Boolean = this.hasAny("solid", "medium")

            return when (idType) {
                IdType.BLOCK -> {
                    if (json.basic()) {
                        StructureType.BLOCK_BASIC.toStructure() // basic structure, same as old version
                    }
                    else if (json.compound()) {
                        determineCompoundBlockStructure(json, id)
                    }
                    else if (json.hasAll("base", "states")) {
                        StructureType.BLOCK_STATES_MODIFIER.toStructure()
                    }
                    else if (json.has("states")) {
                        val states = json["states"].asJsonObject
                        // check to make sure we have a valid default state, otherwise return an error structure
                        // technically this check will succeed if the default state has solid or medium but the internal format of those is invalid, we'll just do that check later.
                        if (!states.has("default"))
                            throw MassJsonParseException("states object does not have a default state, which is required.", id)
                        if (!states["default"].asJsonObject.hasAny(blockValues) && !states["default"].asJsonObject.hasAny("solid", "medium"))
                            throw MassJsonParseException("default state is not in a valid format, but default is required.", id)
                        // uwu~ *notices your valid default state* o- oh!

                        val fullSize = states.size()

                        states.keySet().forEach {
                            if (!stateRegex.test(it)) {
                                logger.error("$it does not match regex for blockstate strings, skipping.")
                                states.remove(it) // remove invalid states completely
                            }
                        }

                        val state2StructureType = mutableMapOf<String, StructureType>()
                        states.asMap().forEach { (state, json) ->
                            json as JsonObject
                            if (json.basic())
                                state2StructureType[state] = StructureType.BLOCK_BASIC
                            else if (json.compound()) {
                                try {
                                    val structure = determineCompoundBlockStructure(json, "$id[$state]")

                                    if (structure.isWarn())
                                        logger.warn("warning while parsing blockstate $id[$state]: ${structure.warn}")

                                    state2StructureType[state] = structure.type
                                } catch (parseE: MassJsonParseException) {
                                    logger.error(parseE.getParseError())
                                    states.remove(state)
                                }
                            } else { // state format isn't valid here for obvious reasons
                                logger.error("invalid format for $id[$state], skipping this block state")
                                states.remove(state)
                            }
                        }

                        if (states.size() == 0) {
                            throw MassJsonParseException("No valid block states for $id!")
                        }

                        Structure(StructureType.BLOCK_STATES, warn = if (states.size() == 1 && fullSize > 1) "The default state in block $id is the only valid state!" else null, state2StructureType = state2StructureType)
                    }
                    else {
                        throw MassJsonParseException("Could not determine block structure", id)
                    } // sowwy >.<
                }
                IdType.FLUID -> {
                    if (json.basic(false)) {
                        StructureType.FLUID_BASIC.toStructure()
                    } else if (json.has("states")) {
                        val states = json["states"].asJsonObject
                        if (!states.has("default"))
                            throw MassJsonParseException("states object does not have a default fluid state, which is required.", id)
                        if (!states["default"].asJsonObject.basic(false))
                            throw MassJsonParseException("default state is not in a valid format, but default is required.", id)

                        val fullSize = states.size()

                        states.keySet().forEach {
                            if (!stateRegex.test(it)) {
                                logger.error("$it does not match regex for fluidstate strings, skipping.")
                                states.remove(it)
                            }
                        }

                        val state2StructureType = mutableMapOf<String, StructureType>()
                        states.asMap().forEach { (state, json) ->
                            json as JsonObject
                            if (json.basic()) {
                                state2StructureType[state] = StructureType.FLUID_BASIC
                            } else {
                                logger.error("invalid format for $id[$state], skipping this fluidstate")
                                states.remove(state)
                            }
                        }

                        if (states.size() == 0) {
                            throw MassJsonParseException("No valid fluid states for $id!")
                        }

                        Structure(StructureType.FLUID_STATES, warn = if (states.size() == 1 && fullSize > 1) "The default state in fluid $id is the only valid state!" else null, state2StructureType = state2StructureType)
                    } else throw MassJsonParseException("Could not determine fluid structure!", id)
                }
                IdType.BLOCK_TAG -> {
                    if (json.basic()) {
                        StructureType.BLOCK_TAG_BASIC.toStructure()
                    } else if (json.compound()) {
                        determineCompoundBlockStructure(json, id, true) // i could implement states for tags but also that doesn't really make sense
                    } else throw MassJsonParseException("Could not determine block tag structure!", id)
                }
                IdType.FLUID_TAG -> {
                    if (json.hasAny(fluidValues)) {
                        StructureType.FLUID_TAG_BASIC.toStructure()
                    } else throw MassJsonParseException("Could not determine fluid tag structure!", id)
                }
                IdType.NONE -> throw MassJsonParseException("how") // this shouldn't be possible but we're checking anyways because i have anxiety
            }
        }

        private fun parseNumericValue(element: JsonElement?, default: Double): NumericValue {
            if (element == null) return NumericValue.Literal(default)
            return when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> NumericValue.Literal(element.asDouble)
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val target = element.asString
                    when (target) {
                        "default" -> {
                            NumericValue.Default(1.0)
                        }
                        else -> {
                            val targetState = idAndProperties(target)
                            NumericValue.Dependent(targetState.a, targetState.b, 1.0)
                        }
                    }
                }
                element.isJsonObject -> {
                    val json = element.asJsonObject
                    val target = json["value"]?.asString
                    val mult = json["mult"]?.asDouble ?: 1.0

                    when (target) {
                        null -> {
                            logger.error("no target value in dependent value, defaulting to $default")
                            NumericValue.Literal(default)
                        }
                        "default" -> {
                            NumericValue.Default(mult)
                        }
                        else -> {
                            val targetState = idAndProperties(target)
                            NumericValue.Dependent(targetState.a, targetState.b, mult)
                        }
                    }
                }
                else -> {
                    logger.error("invalid numeric value format, using default $default.")
                    NumericValue.Literal(default)
                }
            }
        }

        /**
         * Parses a single entry for a block or fluid.
         */
        private fun parse(element: JsonElement, origin: ResourceLocation, index: Int = -1) {
            val json: JsonObject = element.asJsonObject
            if (json.has("ignore")) {
                if (index == -1) {
                    logger.info("Ignoring entry $origin")
                } else {
                    logger.info("Ignoring entry at index $index in $origin")
                }
                return
            }
            val idType = determineId(json)

            if (idType == IdType.NONE) {
                var message = "error parsing $origin: Could not find member for a valid fluid, block, or tag id"
                if (index != -1) {
                    message += " in element $index" // tell the user which element this error occurred in
                }
                logger.error(message)
                return
            }

            val id = idType.getId(json)
            if (!ResourceLocation.isValidResourceLocation(id)) {
                logger.error("error while parsing entry: $id is not a valid id!")
                return
            }

            val priority = json["priority"]?.asInt ?: decideDefaultPriority(origin)

            val structure: Structure

            try {
                structure = determineStructure(json, idType, id)
            } catch (parseE: MassJsonParseException) {
                logger.error(parseE.getParseError()) // parseE jackson
                return
            }

            if (structure.isWarn())
                logger.warn("warning while parsing entry for $id: ${structure.warn}")
            // all clear

            when (structure.type) {
                StructureType.BLOCK_BASIC -> {
                    putBlockProperties(id, "default", PendingBlockProperties(priority, parseSolid(json)))
                }
                StructureType.BLOCK_COMPOUND -> {
                    putBlockProperties(id, "default", PendingBlockProperties(
                        priority,
                        if (json.has("solid")) parseSolid(json.getAsJsonObject("solid")) else null,
                        if (json.has("medium")) parseMedium(json.getAsJsonObject("medium")) else null,
                        if (json.has("displacement")) parseShape(json.getAsJsonArray("displacement")) else null
                    ))
                }
                StructureType.BLOCK_STATES -> {
                    structure.state2StructureType!!.forEach { (state, type) ->
                        when (type) {
                            StructureType.BLOCK_BASIC -> {
                                putBlockProperties(id, state, PendingBlockProperties(priority, parseSolid(json.getAsJsonObject(state))))
                            }
                            StructureType.BLOCK_COMPOUND -> {
                                val stateJson = json.getAsJsonObject(state)
                                putBlockProperties(id, state, PendingBlockProperties(
                                    priority,
                                    if (json.has("solid")) parseSolid(stateJson.getAsJsonObject("solid")) else null,
                                    if (json.has("medium")) parseMedium(stateJson.getAsJsonObject("medium")) else null,
                                    if (json.has("displacement")) parseShape(stateJson.getAsJsonArray("displacement")) else null
                                ))
                            }
                            else -> {
                                logger.error("what are you doing this should be impossible to reach what (id: $id, index: $index, origin: $origin)")
                            }
                        }
                    }
                }
                StructureType.BLOCK_STATES_MODIFIER -> {}

                StructureType.FLUID_BASIC -> {
                    putFluidProperties(id, "default", PendingFluidProperties(priority, parseLiquid(json)))
                }
                StructureType.FLUID_STATES -> { // type really doesn't matter here because fluids can only be parsed one way, but i don't feel like adding a list of states to this, map works just fine
                    structure.state2StructureType!!.keys.forEach { state ->
                        putFluidProperties(id, state, PendingFluidProperties(priority, parseLiquid(json.getAsJsonObject(state))))
                    }
                }
                // tags
                StructureType.BLOCK_TAG_BASIC -> {
                    putBlockTag(id, BlockTagProperties(priority, PendingBlockProperties(priority, parseSolid(json)), parseTagExclusions(json)))
                }
                StructureType.BLOCK_TAG_COMPOUND -> {
                    putBlockTag(id, BlockTagProperties(priority, PendingBlockProperties(
                        priority,
                        if (json.has("solid")) parseSolid(json.getAsJsonObject("solid")) else null,
                        if (json.has("medium")) parseMedium(json.getAsJsonObject("medium")) else null,
                        if (json.has("displacement")) parseShape(json.getAsJsonArray("displacement")) else null
                    ), parseTagExclusions(json)))
                }
                StructureType.BLOCK_TAG_STATES -> {}

                StructureType.FLUID_TAG_BASIC -> {
                    putFluidTag(id, FluidTagProperties(priority, PendingFluidProperties(priority, parseLiquid(json)), parseTagExclusions(json)))
                }
            }
        }

        // region parse functions
        private fun parseSolid(json: JsonObject): PendingSolidProperties {
            val mass = parseNumericValue(json["mass"], VSGameConfig.SERVER.blockProperties.defaultBlockMass)
            val friction = parseNumericValue(json["friction"], VSGameConfig.SERVER.blockProperties.defaultBlockFriction)
            val elasticity = parseNumericValue(json["elasticity"], VSGameConfig.SERVER.blockProperties.defaultBlockElasticity)
            val hardness = parseNumericValue(json["hardness"], VSGameConfig.SERVER.blockProperties.defaultBlockHardness)
            val noCollision = json["no_collision"]?.asBoolean ?: false
            val shapeOverride = json["shape_override"]?.let { parseShape(it) }
            return PendingSolidProperties(mass, friction, elasticity, hardness, noCollision, shapeOverride)
        }

        private fun parseMedium(json: JsonObject): PendingMediumProperties {
            val dragCoefficient = parseNumericValue(json["drag"], VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient)
            val shape = json["shape_override"]?.let { parseShape(it) }

            return PendingMediumProperties(dragCoefficient, shape)
        }

        private fun parseLiquid(json: JsonObject): PendingLiquidProperties {
            val density = parseNumericValue(json["density"], VSGameConfig.SERVER.blockProperties.defaultLiquidDensity)
            val dragCoefficient = parseNumericValue(json["drag"], VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient)

            val velocityArray = json["velocity"]?.asJsonArray
            val velocity = if (velocityArray != null) {
                Vector3d(velocityArray[0].asDouble, velocityArray[1].asDouble, velocityArray[2].asDouble)
            } else {
                Vector3d()
            }

            val shapeOverride = json["shape_override"]?.let { parseShape(it) }

            return PendingLiquidProperties(density, dragCoefficient, velocity, shapeOverride)
        }

        private fun parseShape(jsonArray: JsonElement): AABBic? =
            if (jsonArray.isJsonArray) {
                jsonArray as JsonArray
                if (jsonArray.size() != 6) null else {
                    AABBi(jsonArray[0].asInt, jsonArray[1].asInt, jsonArray[2].asInt,
                        jsonArray[3].asInt, jsonArray[4].asInt, jsonArray[5].asInt)
                }
            } else null

        private fun parseTagExclusions(json: JsonObject): Set<ResourceLocation> {
            val set = mutableSetOf<ResourceLocation>()
            if (json.has("exclude") && json.get("exclude").isJsonArray) {
                val exclusionArray = json.get("exclude").asJsonArray
                exclusionArray.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }.forEach {
                    if (ResourceLocation.isValidResourceLocation(it.asString)) set.add(ResourceLocation.of(it.asString, ':'))
                }
            }
            return set.toSet()
        }
        // endregion
    }

    fun JsonObject.hasAny(members: Iterable<String>): Boolean = members.any { has(it) }
    fun JsonObject.hasAny(vararg members: String): Boolean = hasAny(members.asIterable())
    fun JsonObject.hasAll(vararg members: String): Boolean = members.all { has(it) }

    fun <K, V> Map<K, V>.getOrOther(key: K, other: K): V? = if (key == other) get(key) else get(key) ?: get(other)

    private val logger by logger()
}
