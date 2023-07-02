package org.valkyrienskies.mod.util

import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.impl.datastructures.DenseBlockPosSet
import org.valkyrienskies.core.impl.game.ships.ConnectivityForest
import org.valkyrienskies.core.impl.game.ships.ConnectivityForestImpl
import org.valkyrienskies.core.impl.game.ships.ShipDataCommon
import org.valkyrienskies.core.impl.game.ships.ShipTransformImpl
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.util.toBlockPos

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
                            for (snap in shipsToSplit) {
                                val toRemove = DenseBlockPosSet()
                                for (vertex in snap.first.values.toList()) {
                                    if (vertex != null) {
                                        toRemove.add(vertex.posX, vertex.posY, vertex.posZ)
                                    }
                                }
                                val centerPos = snap.second
                                if (!toRemove.contains(centerPos)) {
                                    logger.warn("Center position is not in the set of blocks to assemble... huh?!")
                                }
                                if (toRemove.isEmpty()) {
                                    logger.error("List of blocks to assemble is empty... how did we get here? Aborting.")
                                    forest.removeFromBreakQueue(breakage)
                                    return
                                }

                                val newShip : ServerShip = createNewShipWithBlocks(centerPos.toBlockPos(), toRemove, self)

                                val centerPosCentered = Vector3d(centerPos).add(0.5, 0.5, 0.5)

                                logger.debug("Split a segment of a ship with ID ${ship.id} into new ship with ID ${newShip.id} and slug ${newShip.slug}!")

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
                            }
                            forest.removeFromBreakQueue(breakage)
                        }
                    }
                }
            }
        }
    }
}
