package org.valkyrienskies.mod.util

import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.impl.datastructures.DenseBlockPosSet
import org.valkyrienskies.core.impl.game.ships.ConnectivityForest
import org.valkyrienskies.core.impl.game.ships.ConnectivityForestImpl
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.util.toBlockPos

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
                            for (vertex in shipToSplit.values) {
                                toRemove.add(vertex!!.posX, vertex.posY, vertex.posZ)
                            }
                            var centerPos = breakage.first
                            if (isShipTwo) {
                                centerPos = breakage.second
                            }

                            // todo: transfer velocity/omega to new ship
                            val newShip : ServerShip = createNewShipWithBlocks(centerPos.toBlockPos(), toRemove, self)
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
