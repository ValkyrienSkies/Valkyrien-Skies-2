package org.valkyrienskies.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.valkyrienskies.core.api.ShipProvider
import org.valkyrienskies.core.game.ships.ShipObject

interface BlockEntityShipProvider : ShipProvider {

    /**
     * Gets called on block entity creation and relocation
     *  the actual new [BlockPos] and [Level] shall be assigned after this call
     *  on the fields of [BlockEntity] themselves too
     * @param newPos
     * @param newLevel
     * @param newShip
     */
    fun shipChange(newPos: BlockPos, newLevel: Level, newShip: ShipObject)
}
