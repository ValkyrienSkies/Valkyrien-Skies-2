package org.valkyrienskies.mod.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.valkyrienskies.core.api.ServerShip
import org.valkyrienskies.core.api.ServerShipUser
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.core.game.ships.ShipObject

interface ShipBlockEntity : ServerShipUser {
    override var ship: ServerShip?

    /**
     * Gets called on block entity creation and relocation
     *  the actual new [BlockPos], [Ship] and [Level] shall be assigned
     *  on this [BlockEntity] after this call
     * @param newPos
     * @param newLevel
     * @param newShip
     */
    fun onShipChange(newPos: BlockPos, newLevel: Level, newShip: ShipObject) {}
}
