package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.joml.Vector3ic
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus.CONNECTED
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus.DISCONNECTED
import org.valkyrienskies.core.api.world.connectivity.SparseVoxelPosition
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.api.getShipById
import org.valkyrienskies.mod.api.toBlockPos
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.util.logger
import java.util.function.Consumer

class SplitHandler(private val doEdges: Boolean, private val doCorners: Boolean) {

    private val splitQueue: HashMap<DimensionId, HashMap<ShipId, Int>> = hashMapOf()

    fun queueSplit(level: Level, shipId: ShipId?) {
        splitQueue[level.dimensionId]?.put(shipId ?: -1, VSGameConfig.SERVER.defaultSplitGraceTimer) ?: run {
            splitQueue[level.dimensionId] = hashMapOf((shipId ?: -1) to VSGameConfig.SERVER.defaultSplitGraceTimer)
        }
    }

    fun tick(level: ServerLevel) {
        if (splitQueue[level.dimensionId] != null && splitQueue[level.dimensionId]!!.isNotEmpty()) {
            val splitsToProcess = HashSet<ShipId>()
            for (splitIndex in splitQueue[level.dimensionId]!!.keys) {
                if (splitQueue[level.dimensionId]!![splitIndex]!! <= 0) {
                    splitsToProcess.add(splitIndex)
                } else {
                    splitQueue[level.dimensionId]!![splitIndex] = splitQueue[level.dimensionId]!![splitIndex]!! - 1
                }
            }
            splitsToProcess.forEach {
                splitQueue[level.dimensionId]!!.remove(it)
                split(level, it)
            }
        }
    }

    fun split(level: Level, shipId: ShipId, after: Consumer<ServerShip>? = null) {
        if (level is ServerLevel) {
            val loadedShip : LoadedServerShip? = level.shipObjectWorld.loadedShips.getById(shipId)
            if ((loadedShip != null && loadedShip.getAttachment<SplittingDisablerAttachment>()?.canSplit() != false) || (loadedShip == null && VSGameConfig.SERVER.enableWorldSplitting)) {
                if (true) {

                    val blockNeighbors = HashSet(level.shipObjectWorld.getAllSolidComponentsFromClaim(loadedShip?.chunkClaimDimension ?: return, loadedShip?.chunkClaim ?: return))

                    if (blockNeighbors.isNotEmpty()) {
                        //find largest remaining component
                        var largestComponentNode: BlockPos = blockNeighbors.first().toBlockPos()
                        var largestComponentSize: Long = -1

                        for (neighborPos in blockNeighbors) {
                            if (level.shipObjectWorld.isIsolatedSolid(neighborPos.x(), neighborPos.y(), neighborPos.z(), level.dimensionId) == DISCONNECTED) {
                                val size = level.shipObjectWorld.getSolidComponentSize(neighborPos.x(), neighborPos.y(), neighborPos.z(), level.dimensionId)
                                if (size > largestComponentSize) {
                                    largestComponentNode = neighborPos.toBlockPos()
                                    largestComponentSize = size
                                }
                            }
                        }

                        if (largestComponentSize == -1L) {
                            return
                        }

                        blockNeighbors.remove(largestComponentNode.toJOML())

                        // use largest as base

                        //find all disconnected components

                        val disconnected = HashSet<BlockPos>()
                        for (neighborPos in blockNeighbors) {
                            if (level.shipObjectWorld.isIsolatedSolid(neighborPos.x(), neighborPos.y(), neighborPos.z(), level.dimensionId) == DISCONNECTED) {
                                if (neighborPos != largestComponentNode) {
                                    if (level.shipObjectWorld.isConnectedBySolid(largestComponentNode.x, largestComponentNode.y, largestComponentNode.z, neighborPos.x(), neighborPos.y(), neighborPos.z(), level.dimensionId) == DISCONNECTED) {
                                        disconnected.add(neighborPos.toBlockPos())
                                    }
                                }
                            }
                        }

                        //check if any disconnected components are connected
                        val toIgnore: HashSet<BlockPos> = HashSet()
                        //toIgnore.add(BlockPos(x, y, z))
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
                                if (level.shipObjectWorld.isIsolatedSolid(otherComponent.x, otherComponent.y, otherComponent.z, level.dimensionId) == CONNECTED) {
                                    if (!toIgnore.contains(otherComponent) && !toIgnore.contains(component)) {
                                        toIgnore.add(component)
                                    }
                                }
                            }
                        }

                        disconnected.removeAll(toIgnore)

                        if (disconnected.isEmpty()) {
                            return
                        } else {
                            loadedShip?.getAttachment(SplittingDisablerAttachment::class.java)?.disableSplitting()
                        }

                        //begin the DFSing

                        val toAssemble = HashSet<Set<BlockPos>>()

                        fun getAllBlocksFromSparseVoxelSet(set: Set<SparseVoxelPosition>): Set<BlockPos> {
                            val blocks = HashSet<BlockPos>()
                            for (voxel in set) {
                                blocks.addAll(voxel.getContaining().map(Vector3ic::toBlockPos))
                            }
                            return blocks
                        }

                        for (starter in disconnected) {
                            if (level.shipObjectWorld.isIsolatedSolid(starter.x, starter.y, starter.z, level.dimensionId) == DISCONNECTED) {
                                val component = level.shipObjectWorld.indexSolidComponentVoxels(starter.x, starter.y, starter.z, level.dimensionId)
                                toAssemble.add(getAllBlocksFromSparseVoxelSet(component))
                            }
                        }

                        if (toAssemble.isEmpty()) {
                            loadedShip?.getAttachment(SplittingDisablerAttachment::class.java)?.enableSplitting()
                            return
                        }

                        for (component in toAssemble) {
                            if (component.isEmpty()) continue
                            val newShip = ShipAssembler.assembleToShip(level, component, 1.0)
                            if (after != null) after.accept(newShip)
                        }

                        loadedShip?.getAttachment(SplittingDisablerAttachment::class.java)?.enableSplitting()
                    }
                }
            }
        }
    }

    companion object {

        val SPLITLOGGER = logger("(Valkyrien Skies) kitkat factory")

        val offsetsToCheck: ArrayList<Vec3i> = arrayListOf(
            Vec3i(1, 0, 0),
            Vec3i(-1, 0, 0),
            Vec3i(0, 1, 0),
            Vec3i(0, -1, 0),
            Vec3i(0, 0, 1),
            Vec3i(0, 0, -1)
        )

        fun getOffsets(doEdges: Boolean, doCorners: Boolean): ArrayList<Vec3i> {
            val list = ArrayList<Vec3i>(offsetsToCheck)
            if (doEdges) { //later: check block edge connectivity config
                list.add(Vec3i(1, 1, 0))
                list.add(Vec3i(1, -1, 0))
                list.add(Vec3i(-1, 1, 0))
                list.add(Vec3i(-1, -1, 0))
                list.add(Vec3i(1, 0, 1))
                list.add(Vec3i(1, 0, -1))
                list.add(Vec3i(-1, 0, 1))
                list.add(Vec3i(-1, 0, -1))
                list.add(Vec3i(0, 1, 1))
                list.add(Vec3i(0, 1, -1))
                list.add(Vec3i(0, -1, 1))
                list.add(Vec3i(0, -1, -1))
            }
            if (doCorners) { //later: check block corner connectivity config
                list.add(Vec3i(1, 1, 1))
                list.add(Vec3i(1, 1, -1))
                list.add(Vec3i(1, -1, 1))
                list.add(Vec3i(1, -1, -1))
                list.add(Vec3i(-1, 1, 1))
                list.add(Vec3i(-1, 1, -1))
                list.add(Vec3i(-1, -1, 1))
                list.add(Vec3i(-1, -1, -1))
            }
            return list
        }
    }
}
