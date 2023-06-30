package org.valkyrienskies.mod.util

import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.impl.datastructures.DenseBlockPosSet
import org.valkyrienskies.core.impl.game.ships.ConnectivityForest
import org.valkyrienskies.core.impl.game.ships.ConnectivityForestImpl
import org.valkyrienskies.core.impl.networking.NetworkChannel
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.util.toBlockPos
import kotlin.math.roundToInt

object ShipSplitter {
    fun splitShips(shipObjectWorld: ServerShipWorldCore, self: ServerLevel) {
        for (ship: LoadedServerShip? in shipObjectWorld.loadedShips) {
            if (ship != null) {
                if (ship.getAttachment<ConnectivityForest>(ConnectivityForest::class.java) != null) {
                    val forest : ConnectivityForestImpl = ship.getAttachment<ConnectivityForest>(ConnectivityForest::class.java) as ConnectivityForestImpl
                    if (forest.breakages.isNotEmpty()) {
                        for (breakage in forest.breakages) {
                            val (shipToSplit, isShipTwo) = forest.split(breakage.first, breakage.second)
                            val toRemove = DenseBlockPosSet()
                            for (vertex in shipToSplit.values.toList()) {
                                if (vertex != null) {
                                    toRemove.add(vertex.posX, vertex.posY, vertex.posZ)
                                }
                            }
                            var centerPos = breakage.first
                            if (isShipTwo) {
                                centerPos = breakage.second
                            }
                            if (!toRemove.contains(centerPos)) {
                                NetworkChannel.logger.warn("Center position is not in the set of blocks to assemble... huh?!")
                            }
                            if (toRemove.isEmpty()) {
                                NetworkChannel.logger.warn("List of blocks to assemble is empty... how did we get here?")
                            }
                            // todo: transfer velocity/omega to new ship

                            val transformedCenter = ship.shipToWorld.transformPosition(Vector3d(centerPos))

                            val newShip : ServerShip = createNewShipWithBlocks(Vector3i(transformedCenter.x.roundToInt(), transformedCenter.y.roundToInt(), transformedCenter.z.roundToInt()).toBlockPos(), toRemove, self)
                            NetworkChannel.logger.info("Split a segment of a ship with ID ${ship.id} into new ship with ID ${newShip.id} and slug ${newShip.slug}!")
                            val velVec = Vector3d(ship.velocity)
                            val omegaVec = Vector3d(ship.omega)

                            forest.breakages.remove(breakage)
                        }
                    }
                }
            }
        }
    }
}
