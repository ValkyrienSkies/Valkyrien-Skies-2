package org.valkyrienskies.mod.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Rotation
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.impl.datastructures.DenseBlockPosSet
import org.valkyrienskies.core.impl.datastructures.dynconn.BlockPosVertex
import org.valkyrienskies.core.impl.game.ships.AirPocketForest
import org.valkyrienskies.core.impl.game.ships.AirPocketForestImpl
import org.valkyrienskies.core.impl.game.ships.ConnectivityForest
import org.valkyrienskies.core.impl.game.ships.ConnectivityForestImpl
import org.valkyrienskies.core.impl.game.ships.ShipDataCommon
import org.valkyrienskies.core.impl.game.ships.ShipTransformImpl
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ShipSplitEvent
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toBlockPos
import org.valkyrienskies.mod.common.util.toJOML

object ShipSplitter {
    fun splitShips(shipObjectWorld: ServerShipWorldCore, self: ServerLevel) {
        for (ship: LoadedServerShip? in shipObjectWorld.loadedShips) {
            if (ship != null) {
                if (ship.getAttachment<ConnectivityForest>(ConnectivityForest::class.java) != null) {
                    val forest : ConnectivityForestImpl = ship.getAttachment<ConnectivityForest>(ConnectivityForest::class.java) as ConnectivityForestImpl
                    forest.gameTick()
                    if (forest.getBreakQueue().isNotEmpty()) {
                        val logger by logger("ShipSplitter")

                        for (breakage in forest.getBreakQueue()) {

                            val shipsToSplit = forest.split(breakage)
                            for (snap in shipsToSplit.first) {
                                val toRemove = DenseBlockPosSet()
                                for (vertex in snap.first.values.toList()) {
                                    if (vertex != null) {
                                        if (!self.getBlockState(BlockPos(vertex.posX, vertex.posY, vertex.posZ)).isAir) {
                                            toRemove.add(vertex.posX, vertex.posY, vertex.posZ)
                                        }
                                    }
                                }

                                val centerPos = snap.second
                                if (toRemove.isEmpty()) {
                                    // logger.error("List of blocks to assemble is empty... how did we get here? Aborting.")

                                    forest.removeFromBreakQueue(breakage)
                                    return
                                }

                                val newShip : ServerShip = createNewShipWithBlocks(centerPos.toBlockPos(), toRemove, self)

                                val centerPosCentered = Vector3d(centerPos).add(0.5, 0.5, 0.5)

                                logger.info("Split a segment of a ship with ID ${ship.id} into new ship with ID ${newShip.id} and slug ${newShip.slug}!")

                                val shipChunkX = newShip.chunkClaim.xMiddle
                                val shipChunkZ = newShip.chunkClaim.zMiddle

                                val centerInShip: Vector3dc = Vector3d(
                                    ((shipChunkX shl 4) + (centerPos.x() and 15).toDouble()),
                                    centerPos.y().toDouble(),
                                    (shipChunkZ shl 4) + (centerPos.z() and 15).toDouble()
                                )

                                val scaling = ship.transform.shipToWorldScaling
                                val offset: Vector3dc =
                                    newShip.inertiaData.centerOfMassInShip.sub(centerInShip, Vector3d())

                                val posInWorld = ship.transform.shipToWorld.transformPosition(centerPosCentered.add(offset, Vector3d()), Vector3d())
                                // val offset = newShip.inertiaData.centerOfMassInShip.sub(oldPosInWorld , Vector3d())
                                // val posInWorld = ship.transform.shipToWorld.transformPosition(centerPosCentered.add(offset, Vector3d()), Vector3d())


                                val rotInWorld = ship.transform.shipToWorldRotation

                                val velVec = Vector3d(ship.velocity)
                                val omegaVec = Vector3d(ship.omega)

                                val newShipTransform = ShipTransformImpl(posInWorld, newShip.inertiaData.centerOfMassInShip, rotInWorld, scaling)

                                (newShip as ShipDataCommon).transform = newShipTransform
                                (newShip as ShipDataCommon).physicsData.linearVelocity = velVec
                                (newShip as ShipDataCommon).physicsData.angularVelocity = omegaVec

                                verifyShip(self, newShip, centerPos)

                                val event = ShipSplitEvent(ship, newShip, shipsToSplit.second, snap.second)
                                VSGameEvents.shipSplit.emit(event)
                            }

                            verifyShip(self, ship, shipsToSplit.second)

                            forest.removeFromBreakQueue(breakage)
                        }
                    }
                }
            }
        }
    }

    fun verifyShip(level: ServerLevel, newShip: ServerShip, centerPos: Vector3ic) {
        val newLoadedShip: LoadedServerShip = level.shipObjectWorld.loadedShips.getById(newShip.id) ?: return

        val forest: ConnectivityForestImpl = newLoadedShip.getAttachment<ConnectivityForest>(ConnectivityForest::class.java) as ConnectivityForestImpl ?: return

        val root: BlockPosVertex = forest.vertices[centerPos] ?: return

        forest.vertices.values.forEach {
            if (!forest.graph.connected(it, root)) {
                val list: ArrayList<Vector3ic?> = ArrayList()
                list.add(it.toJOML())
                list.add(root.toJOML())
                forest.addToBreakQueue(list)
            }
        }
    }

    fun fuseShips(level: ServerLevel, baseShip: LoadedServerShip, shipToFuse: LoadedServerShip, fuseTo: Vector3ic, fuseFrom: Vector3ic): Boolean {
        val fuseForest: ConnectivityForestImpl = shipToFuse.getAttachment<ConnectivityForest>(ConnectivityForest::class.java) as ConnectivityForestImpl
        val logger by logger("ShipFuser")

        logger.info("Fusing ship ${shipToFuse.id} into ship ${baseShip.id} at point ${fuseTo}.")

        val verticesSafe = fuseForest.vertices.values.toList()

        verticesSafe.forEach { vertex ->
            val pos = BlockPos(vertex.posX, vertex.posY, vertex.posZ)

            val newPos = (fuseFrom.sub(pos.toJOML(), Vector3i()).add(fuseTo, Vector3i())).toBlockPos()
            val oldChunk = level.getChunkAt(pos)
            val newChunk = level.getChunkAt(newPos)

            relocateBlock(oldChunk, pos, newChunk, newPos, true, baseShip, Rotation.NONE)
        }
        return true
    }

    fun airHandle(shipObjectWorld: ServerShipWorldCore, self: ServerLevel) {
        for (loadedShip in shipObjectWorld.loadedShips) {
            if (loadedShip.getAttachment(AirPocketForest::class.java) != null) {
                val airPocketForest : AirPocketForestImpl = loadedShip.getAttachment(AirPocketForest::class.java) as AirPocketForestImpl

                if (airPocketForest.shouldUpdateOutsideAir) {

                }
            }
        }
    }
}
