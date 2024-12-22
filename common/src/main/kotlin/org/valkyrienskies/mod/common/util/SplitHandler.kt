package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.joml.primitives.AABBi
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus.CONNECTED
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus.DISCONNECTED
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.util.logger

class SplitHandler(private val doEdges: Boolean, private val doCorners: Boolean) {

    fun split(level: Level, x: Int, y: Int, z: Int, prevBlockState: BlockState, newBlockState: BlockState) {
        if (level is ServerLevel) {
            val loadedShip = level.getShipObjectManagingPos(x shr 4, z shr 4)
            if (loadedShip != null && loadedShip.getAttachment<SplittingDisablerAttachment>()?.canSplit() != false) {
                if (!prevBlockState.isAir && newBlockState.isAir) {
                    val blockNeighbors: HashSet<BlockPos> = HashSet()

                    val shipBox = loadedShip.shipAABB?.expand(1, AABBi()) ?: return

                    for (neighborOffset in getOffsets(doEdges, doCorners)) {
                        val neighborPos = BlockPos(x + neighborOffset.x, y + neighborOffset.y, z + neighborOffset.z)
                        val neighborState = level.getBlockState(neighborPos)
                        if (!neighborState.isAir && neighborPos != BlockPos(x, y, z) && shipBox.containsPoint(neighborPos.toJOML())) {
                            blockNeighbors.add(neighborPos)
                        }
                    }

                    if (blockNeighbors.isNotEmpty()) {
                        //find largest remaining component
                        var largestComponentNode: BlockPos = blockNeighbors.first()
                        var largestComponentSize: Long = -1

                        for (neighborPos in blockNeighbors) {
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
                                        disconnected.add(neighborPos)
                                    }
                                }
                            }
                        }

                        //check if any disconnected components are connected
                        val toIgnore: HashSet<BlockPos> = HashSet()
                        toIgnore.add(BlockPos(x, y, z))
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
                        } else {
                            loadedShip.getAttachment(SplittingDisablerAttachment::class.java)?.disableSplitting()
                        }

                        //begin the DFSing

                        val toAssemble = HashSet<DenseBlockPosSet>()

                        for (starter in disconnected) {
                            val visited = DenseBlockPosSet()
                            val queuedPositions = HashSet<BlockPos>()
                            queuedPositions.add(starter)

                            while (queuedPositions.isNotEmpty()) {
                                val current = queuedPositions.first()
                                queuedPositions.remove(current)
                                visited.add(current.toJOML())
                                val toCheck = HashSet<BlockPos>()
                                for (offset in getOffsets(doEdges, doCorners)) {
                                    toCheck.add(
                                        BlockPos(current.x + offset.x, current.y + offset.y, current.z + offset.z)
                                    )
                                }
                                for (check in toCheck) {
                                    if (!visited.contains(check.toJOML()) && !level.getBlockState(check).isAir) {
                                        queuedPositions.add(check)
                                    }
                                }
                            }
                            //if we have visited all blocks in the component, we can split it
                            toAssemble.add(visited)
                        }

                        if (toAssemble.isEmpty()) {
                            loadedShip.getAttachment(SplittingDisablerAttachment::class.java)?.enableSplitting()
                            return
                        }

                        for (component in toAssemble) {
                            ShipAssembler.assembleToShip(level, component, true, 1.0, true)
                        }

                        loadedShip.getAttachment(SplittingDisablerAttachment::class.java)?.enableSplitting()
                    }
                }
            }
        }
    }

    companion object {

        val SPLITLOGGER = logger("kitkat factory")

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
