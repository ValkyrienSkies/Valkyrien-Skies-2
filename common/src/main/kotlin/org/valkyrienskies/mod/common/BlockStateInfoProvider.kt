package org.valkyrienskies.mod.common

import com.mojang.serialization.Lifecycle
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.Vec3i
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.ships.Wing
import org.valkyrienskies.core.api.ships.WingManager
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus.CONNECTED
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus.DISCONNECTED
import org.valkyrienskies.core.apigame.world.chunks.BlockType
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.block.WingBlock
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.util.toJOML
import java.util.function.IntFunction

// Other mods can then provide weights and types based on their added content
// NOTE: if we have block's in vs-core we should ask getVSBlock(blockstate: BlockStat): VSBlock since thatd be more handy
//  altough we might want to allow null properties in VSBlock that is returned since we do want partial data fetching
// https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/25
interface BlockStateInfoProvider {
    val priority: Int

    fun getBlockStateMass(blockState: BlockState): Double?

    // Get the id of the block state
    fun getBlockStateType(blockState: BlockState): BlockType?
}

object BlockStateInfo {

    // registry for mods to add their weights
    val REGISTRY = MappedRegistry<BlockStateInfoProvider>(
        ResourceKey.createRegistryKey(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "blockstate_providers")),
        Lifecycle.experimental(),
        null
    )

    private lateinit var SORTED_REGISTRY: List<BlockStateInfoProvider>

    // init { doesn't work since the class gets loaded too late
    fun init() {
        Registry.register(REGISTRY, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "data"), MassDatapackResolver)
        Registry.register(
            REGISTRY, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "default"), DefaultBlockStateInfoProvider
        )

        VSGameEvents.registriesCompleted.on { _, _ -> SORTED_REGISTRY = REGISTRY.sortedByDescending { it.priority } }
    }

    // This is [ThreadLocal] because in single-player games the Client thread and Server thread will read/write to
    // [CACHE] simultaneously. This creates a data race that can crash the game (See https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/126).

    class Cache {
        // Use a load factor of 0.5f because this map is hit very often
        private val blockStateCache: Int2ObjectOpenHashMap<Pair<Double, BlockType>> =
            Int2ObjectOpenHashMap<Pair<Double, BlockType>>(2048, 0.5f)

        fun get(blockState: BlockState): Pair<Double, BlockType>? {
            val blockId = Block.getId(blockState)

            if (blockId == -1) {
                return null
            }

            return blockStateCache.computeIfAbsent(blockId, IntFunction { iterateRegistry(blockState) })
        }
    }

    private val _cache = ThreadLocal.withInitial { Cache() }
    val cache: Cache get() = _cache.get()

    // NOTE: this caching can get allot better, ex. default just returns constants so it might be more faster
    //  if we store that these values do not need to be cached by double and blocktype but just that they use default impl

    // this gets the weight and type provided by providers; or it gets it out of the cache

    fun get(blockState: BlockState): Pair<Double, BlockType>? {
        return cache.get(blockState)
    }

    private fun iterateRegistry(blockState: BlockState): Pair<Double, BlockType> =
        Pair(
            SORTED_REGISTRY.firstNotNullOf { it.getBlockStateMass(blockState) },
            SORTED_REGISTRY.firstNotNullOf { it.getBlockStateType(blockState) },
        )

    // NOTE: this gets called irrelevant if the block is actually on a ship; so it needs to be changed that
    // shipObjectWorld only requests the data if needed (maybe supplier?)
    // NOTE2: spoken of in discord in the future well have prob block's in vs-core with id's and then
    // the above issue shall be fixed
    // https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/25

    fun onSetBlock(level: Level, blockPos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) =
        onSetBlock(level, blockPos.x, blockPos.y, blockPos.z, prevBlockState, newBlockState)

    fun onSetBlock(level: Level, x: Int, y: Int, z: Int, prevBlockState: BlockState, newBlockState: BlockState) {
        if (!::SORTED_REGISTRY.isInitialized) return

        val shipObjectWorld = level.shipObjectWorld

        val (prevBlockMass, prevBlockType) = get(prevBlockState) ?: return

        val (newBlockMass, newBlockType) = get(newBlockState) ?: return

        // region Inject wings
        if (level is ServerLevel) {
            val loadedShip = level.getShipObjectManagingPos(x shr 4, z shr 4)
            if (loadedShip != null) {
                val wingManager = loadedShip.getAttachment(WingManager::class.java)!!
                val wasOldBlockWing = prevBlockState.block is WingBlock
                val newBlockStateBlock = newBlockState.block
                val newWing: Wing? =
                    if (newBlockStateBlock is WingBlock) newBlockStateBlock.getWing(
                        level, BlockPos(x, y, z), newBlockState
                    ) else null
                if (newWing != null) {
                    // Place the new wing
                    wingManager.setWing(wingManager.getFirstWingGroupId(), x, y, z, newWing)
                } else if (wasOldBlockWing) {
                    // Delete the old wing
                    wingManager.setWing(wingManager.getFirstWingGroupId(), x, y, z, null)
                }
            }
        }
        // endregion

        shipObjectWorld.onSetBlock(
            x, y, z, level.dimensionId, prevBlockType, newBlockType, prevBlockMass,
            newBlockMass
        )

        if (level is ServerLevel) {
            val loadedShip = level.getShipObjectManagingPos(x shr 4, z shr 4)
            if (loadedShip != null) {
                if (!prevBlockState.isAir && newBlockState.isAir){
                    val blockNeighbors: HashSet<BlockPos> = HashSet()
                    for (dir in Direction.entries) {
                        val shipBox = loadedShip.shipAABB?.expand(1) ?: continue
                        val neighborPos = BlockPos(x, y, z).relative(dir)
                        val neighborState = level.getBlockState(neighborPos)
                        if (!neighborState.isAir && neighborPos != BlockPos(x, y, z) && shipBox.containsPoint(neighborPos.toJOML())) {
                            blockNeighbors.add(neighborPos)
                        }
                        if (true) { //later: check block edge connectivity config
                            for (secondDir in Direction.entries) {
                                if (dir.axis != secondDir.axis) {
                                    val secondNeighborPos = neighborPos.relative(secondDir)
                                    val secondNeighborState = level.getBlockState(secondNeighborPos)
                                    if (!secondNeighborState.isAir && secondNeighborPos != BlockPos(x, y, z) && shipBox.containsPoint(neighborPos.toJOML())) {
                                        blockNeighbors.add(secondNeighborPos)
                                    }
                                    if (true) { //later: check block corner connectivity config
                                        for (thirdDir in Direction.entries) {
                                            if (dir.axis != secondDir.axis && dir.axis != thirdDir.axis && secondDir.axis != thirdDir.axis) {
                                                val thirdNeighborPos = secondNeighborPos.relative(thirdDir)
                                                val thirdNeighborState = level.getBlockState(thirdNeighborPos)
                                                if (!thirdNeighborState.isAir && thirdNeighborPos != BlockPos(x, y, z) && shipBox.containsPoint(neighborPos.toJOML())) {
                                                    blockNeighbors.add(thirdNeighborPos)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (blockNeighbors.isNotEmpty()) {
                        //find largest remaining component
                        var largestComponentNode: BlockPos = blockNeighbors.first()
                        var largestComponentSize: Long = -1

                        for (neighborPos in blockNeighbors) {
                            print(level.shipObjectWorld.isIsolatedSolid(neighborPos.x, neighborPos.y, neighborPos.z, level.dimensionId))
                            if (level.shipObjectWorld.isIsolatedSolid(neighborPos.x, neighborPos.y, neighborPos.z, level.dimensionId) == DISCONNECTED) {
                                val size = level.shipObjectWorld.getSolidComponentSize(neighborPos.x, neighborPos.y, neighborPos.z, level.dimensionId)
                                if (size > largestComponentSize) {
                                    largestComponentNode = neighborPos
                                    largestComponentSize = size
                                }
                            }
                        }

                        if (largestComponentSize == -1L) {
                            return
                        }

                        blockNeighbors.remove(largestComponentNode)

                        // use largest as base

                        //find all disconnected components

                        val disconnected = HashSet<BlockPos>()
                        for (neighborPos in blockNeighbors) {
                            if (level.shipObjectWorld.isIsolatedSolid(neighborPos.x, neighborPos.y, neighborPos.z, level.dimensionId) == DISCONNECTED) {
                                if (neighborPos != largestComponentNode) {
                                    if (level.shipObjectWorld.isConnectedBySolid(largestComponentNode.x, largestComponentNode.y, largestComponentNode.z, neighborPos.x, neighborPos.y, neighborPos.z, level.dimensionId) == DISCONNECTED) {
                                        val component = HashSet<BlockPos>()
                                        disconnected.add(neighborPos)
                                    }
                                    println("this is " + level.shipObjectWorld.isConnectedBySolid(largestComponentNode.x, largestComponentNode.y, largestComponentNode.z, neighborPos.x, neighborPos.y, neighborPos.z, level.dimensionId).toString())
                                }
                            }
                        }

                        //check if any disconnected components are connected
                        val toIgnore: HashSet<BlockPos> = HashSet()
                        for (component in disconnected) {
                            for (otherComponent in disconnected) {
                                if (component == otherComponent) {
                                    continue
                                }
                                if (level.shipObjectWorld.isConnectedBySolid(component.x, component.y, component.z, otherComponent.x, otherComponent.y, otherComponent.z, level.dimensionId) == CONNECTED) {
                                    if (!toIgnore.contains(otherComponent) && !toIgnore.contains(component)) {
                                        toIgnore.add(component)
                                    }
                                }
                            }
                        }

                        disconnected.removeAll(toIgnore)

                        if (disconnected.isEmpty()) {
                            return
                        }

                        //begin the DFSing

                        val offsetsToCheck: ArrayList<Vec3i> = arrayListOf(
                            Vec3i(1, 0, 0),
                            Vec3i(-1, 0, 0),
                            Vec3i(0, 1, 0),
                            Vec3i(0, -1, 0),
                            Vec3i(0, 0, 1),
                            Vec3i(0, 0, -1)
                        )
                        if (true) { //later: check block edge connectivity config
                            offsetsToCheck.add(Vec3i(1, 1, 0))
                            offsetsToCheck.add(Vec3i(1, -1, 0))
                            offsetsToCheck.add(Vec3i(-1, 1, 0))
                            offsetsToCheck.add(Vec3i(-1, -1, 0))
                            offsetsToCheck.add(Vec3i(1, 0, 1))
                            offsetsToCheck.add(Vec3i(1, 0, -1))
                            offsetsToCheck.add(Vec3i(-1, 0, 1))
                            offsetsToCheck.add(Vec3i(-1, 0, -1))
                            offsetsToCheck.add(Vec3i(0, 1, 1))
                            offsetsToCheck.add(Vec3i(0, 1, -1))
                            offsetsToCheck.add(Vec3i(0, -1, 1))
                            offsetsToCheck.add(Vec3i(0, -1, -1))
                        }
                        if (true) { //later: check block corner connectivity config
                            offsetsToCheck.add(Vec3i(1, 1, 1))
                            offsetsToCheck.add(Vec3i(1, 1, -1))
                            offsetsToCheck.add(Vec3i(1, -1, 1))
                            offsetsToCheck.add(Vec3i(1, -1, -1))
                            offsetsToCheck.add(Vec3i(-1, 1, 1))
                            offsetsToCheck.add(Vec3i(-1, 1, -1))
                            offsetsToCheck.add(Vec3i(-1, -1, 1))
                            offsetsToCheck.add(Vec3i(-1, -1, -1))
                        }

                        val toAssemble = HashSet<List<BlockPos>>()

                        for (starter in disconnected) {
                            val visited = HashSet<BlockPos>()
                            val queuedPositions = HashSet<BlockPos>()
                            queuedPositions.add(starter)

                            while (queuedPositions.isNotEmpty()) {
                                val current = queuedPositions.first()
                                queuedPositions.remove(current)
                                visited.add(current)
                                val toCheck = HashSet<BlockPos>()
                                for (offset in offsetsToCheck) {
                                    toCheck.add(BlockPos(current.x + offset.x, current.y + offset.y, current.z + offset.z))
                                }
                                for (check in toCheck) {
                                    if (!visited.contains(check) && !level.getBlockState(check).isAir) {
                                        queuedPositions.add(check)
                                    }
                                }
                            }
                            //if we have visited all blocks in the component, we can split it
                            toAssemble.add(visited.toList())
                        }

                        if (toAssemble.isEmpty()) {
                            return
                        }

                        for (component in toAssemble) {
                            ShipAssembler.assembleToShip(level, component, true, 1.0)
                        }
                    }
                }
            }
        }
    }
}
