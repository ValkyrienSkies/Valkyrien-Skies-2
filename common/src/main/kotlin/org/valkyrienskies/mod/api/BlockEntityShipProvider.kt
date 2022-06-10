package org.valkyrienskies.mod.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.core.api.ShipProvider
import org.valkyrienskies.core.game.ships.ShipObject

interface BlockEntityShipProvider : ShipProvider {
    override var ship: Ship

    /**
     * Gets called on block entity creation and relocation
     *  the actual new [BlockPos], [Ship] and [Level] shall be assigned
     *  on this [BlockEntity] after this call
     * @param newPos
     * @param newLevel
     * @param newShip
     */
    fun shipChange(newPos: BlockPos, newLevel: Level, newShip: ShipObject) {}
}
