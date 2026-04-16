package org.valkyrienskies.mod.common

import com.mojang.serialization.Lifecycle
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.Wing
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus
import org.valkyrienskies.core.api.world.connectivity.SparseVoxelPosition
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.block.WingBlock
import org.valkyrienskies.mod.common.config.ConfigType
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.util.BuoyancyHandlerAttachment
import java.util.function.IntFunction

// Other mods can then provide weights and types based on their added content
// NOTE: if we have block's in vs-core we should ask getVSBlock(blockstate: BlockStat): VSBlock since thatd be more handy
//  altough we might want to allow null properties in VSBlock that is returned since we do want partial data fetching
// https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/25
interface BlockStateInfoProvider {
    val priority: Int

    fun getBlockStateMass(blockState: BlockState): Double?

    // Get the id of the block state
    fun getBlockStateType(blockState: BlockState): VsiBlockType?
}

object BlockStateInfo {

    // registry for mods to add their weights
    val REGISTRY = MappedRegistry<BlockStateInfoProvider>(
        ResourceKey.createRegistryKey(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "blockstate_providers")),
        Lifecycle.experimental()
    )

    private lateinit var SORTED_REGISTRY: List<BlockStateInfoProvider>

    @JvmStatic
    fun isSortedRegistryInitialized(): Boolean = ::SORTED_REGISTRY.isInitialized

    // init { doesn't work since the class gets loaded too late
    fun init() {
        Registry.register(REGISTRY, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "data"), MassDatapackResolver)
        Registry.register(
            REGISTRY, ResourceLocation(ValkyrienSkiesMod.MOD_ID, "default"), DefaultBlockStateInfoProvider
        )
        SORTED_REGISTRY = REGISTRY.sortedByDescending { it.priority } // why is this even tied to an event dawg
        VSGameEvents.registriesCompleted.on { _, _ -> SORTED_REGISTRY = REGISTRY.sortedByDescending { it.priority } }

        VSGameEvents.configUpdated.on { entries ->
            val defaultMassChanged = entries.any {
                it.configType == ConfigType.SERVER && it.name == "defaultBlockMass"
            }

            if (defaultMassChanged) {
                invalidateCache()
            }
        }
    }

    // This is [ThreadLocal] because in single-player games the Client thread and Server thread will read/write to
    // [CACHE] simultaneously. This creates a data race that can crash the game (See https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/126).

    class Cache {
        // Use a load factor of 0.5f because this map is hit very often
        private val blockStateCache: Int2ObjectOpenHashMap<Pair<Double, VsiBlockType>> =
            Int2ObjectOpenHashMap<Pair<Double, VsiBlockType>>(2048, 0.5f)

        fun get(blockState: BlockState): Pair<Double, VsiBlockType>? {
            val blockId = Block.getId(blockState)

            if (blockId == -1) {
                return null
            }

            return blockStateCache.computeIfAbsent(blockId, IntFunction { iterateRegistry(blockState) })
        }
    }

    private var _cache = ThreadLocal.withInitial { Cache() }
    val cache: Cache get() = _cache.get()

    private fun invalidateCache() {
        _cache = ThreadLocal.withInitial { Cache() }
    }

    // NOTE: this caching can get allot better, ex. default just returns constants so it might be more faster
    //  if we store that these values do not need to be cached by double and blocktype but just that they use default impl

    // this gets the weight and type provided by providers; or it gets it out of the cache

    fun get(blockState: BlockState): Pair<Double, VsiBlockType>? {
        return cache.get(blockState)
    }

    private fun iterateRegistry(blockState: BlockState): Pair<Double, VsiBlockType> =
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
            val loadedShip = level.getLoadedShipManagingPos(x shr 4, z shr 4)
            if (loadedShip != null) {
                val wingManager = loadedShip.wingManager!!
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

        fun Set<SparseVoxelPosition>.centerFromVoxelSet() : Vector3dc {
            val center = Vector3d(0.0, 0.0, 0.0)
            if (this.isEmpty()) {
                return center
            }
            for (voxel in this) {
                center.add(
                    voxel.x.toDouble() + ((voxel.extent - 1L).toDouble() / 2.0) + 0.5,
                    voxel.y.toDouble() + ((voxel.extent - 1L).toDouble() / 2.0) + 0.5,
                    voxel.z.toDouble() + ((voxel.extent - 1L).toDouble() / 2.0) + 0.5
                )
            }
            center.div(this.size.toDouble())
            return center
        }

        if (level is ServerLevel) {
            val loadedShip = level.getLoadedShipManagingPos(x shr 4, z shr 4)
            if (loadedShip != null) {
                if (VSGameConfig.SERVER.enablePocketBuoyancy) {
                    val buoyancyHandler = loadedShip.getAttachment(BuoyancyHandlerAttachment::class.java)
                    val dimension = loadedShip.chunkClaimDimension
                    val allComponentsInClaim = level.shipObjectWorld.getAllAirComponentsFromClaim(dimension, loadedShip.chunkClaim)
                    var newTotal = 0.0
                    var centerSum = Vector3d(0.0, 0.0, 0.0)
                    if (allComponentsInClaim.isNotEmpty()) {
                        for (component in allComponentsInClaim) {
                            if (level.shipObjectWorld.isIsolatedAir(
                                    component.x(), component.y(), component.z(), dimension
                                ) != ConnectionStatus.DISCONNECTED
                            ) continue
                            val componentSize = level.shipObjectWorld.getAirComponentSize(
                                component.x(), component.y(), component.z(), dimension
                            )
                            val componentVoxels = level.shipObjectWorld.indexAirComponentVoxels(
                                component.x(), component.y(), component.z(), dimension
                            )
                            val componentCenter = componentVoxels.centerFromVoxelSet()

                            newTotal += componentSize
                            centerSum.add(
                                componentCenter.x() * componentSize,
                                componentCenter.y() * componentSize,
                                componentCenter.z() * componentSize
                            )
                        }
                    }
                    if (newTotal > 0.0) {
                        centerSum.div(newTotal)
                    } else {
                        centerSum.set(0.0, 0.0, 0.0)
                    }
                    buoyancyHandler?.buoyancyData?.pocketVolumeTotal = newTotal.toDouble()
                    if (loadedShip.shipAABB?.containsPoint(centerSum.x().toFloat(), centerSum.y().toFloat(), centerSum.z().toFloat()) != false) buoyancyHandler?.buoyancyData?.pocketCenterAverage = centerSum
                    //println("is center sum contained within ship aabb?: ${loadedShip.shipAABB?.containsPoint(centerSum.x().toFloat(), centerSum.y().toFloat(), centerSum.z().toFloat())}")
                }
            }
            if (ValkyrienSkiesMod.vsCore.hooks.enableConnectivity) {
                ValkyrienSkiesMod.splitHandler.queueSplit(level, level.getShipManagingPos(x.toDouble(), y.toDouble(), z.toDouble())?.id)
            }
        }


    }

    /**
     * Recalculates mass of a ship. Useful if block masses were changed in a data pack, game config or in VS itself.
     * The ship is made static before any of its physical properties are modified and only returns to original status
     * if recalculation has been successfully completed.
     *
     * NOTE: There is no distinction between masses that were added by placing real blocks and those that were
     * added "manually" by calling onSetBlock. Some addons implement custom masses in this hacky way. Before
     * triggering a remass, these custom masses should be removed.
     *
     * @return false if mass recalculation has failed for any reason
     */
    // TODO: Add a way to manage custom masses in VS itself so that they are trackable on VS side for remassing and so.
    fun remassShip(level: Level, ship: Ship): Boolean {
        if (level !is ServerLevel) return false
        if (ship !is LoadedServerShip) return false
        if (!::SORTED_REGISTRY.isInitialized) return false

        val aabb = ship.shipAABB
        if (aabb == null) return false

        // Last thing we want is something physical happening to our zero-mass ship.
        val wasStatic = ship.isStatic
        ship.isStatic = true

        val (airBlockMass, airBlockType) = get(Blocks.AIR.defaultBlockState()) ?: return false
        // Before we rebuild masses, make sure we have a ship with zero mass and no colliders.
        // Blocks are replaced with air (in physical representation of a ship, no Minecraft blockstates are changed)
        BlockPos.betweenClosed(
            aabb.minX(), aabb.minY(), aabb.minZ(), aabb.maxX(), aabb.maxY(),
            aabb.maxZ()
        ).forEach {
            val state = level.getBlockState(it)
            val (realBlockMass, realBlockType) = get(state) ?: return false
            if (realBlockType != airBlockType) {
                level.shipObjectWorld.onSetBlock(
                    it.x, it.y, it.z,
                    level.dimensionId,
                    realBlockType, airBlockType,
                    0.0, 0.0 // Making the block air without modifying ship mass, CoM and MoI
                )
            }
        }
        // Zeroing out ship mass.
        // This looks wrong but is actually fine. As per ShipInertiaDataImpl, if the resulting ship mass is zero,
        // its CoM and MoI are explicitly zeroed out, bypassing any calculations. Any blockPos inside ship AABB is good
        // for this purpose.
        level.shipObjectWorld.onSetBlock(
            aabb.minX(), aabb.minY(), aabb.minZ(), level.dimensionId,
            airBlockType, airBlockType,
            ship.inertiaData.mass, 0.0
        )
        // Readding all blocks to ship's physics representation (mass, block type)
        BlockPos.betweenClosed(
            aabb.minX(), aabb.minY(), aabb.minZ(), aabb.maxX(), aabb.maxY(),
            aabb.maxZ()
        ).forEach {
            val state = level.getBlockState(it)
            val (realBlockMass, realBlockType) = get(state) ?: return false
            if (realBlockType != airBlockType) {
                level.shipObjectWorld.onSetBlock(
                    it.x, it.y, it.z,
                    level.dimensionId,
                    airBlockType, realBlockType,
                    0.0, realBlockMass
                )
            }
        }
        ship.isStatic = wasStatic

        return true
    }
}
