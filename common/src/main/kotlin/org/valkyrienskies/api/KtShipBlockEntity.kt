package org.valkyrienskies.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.valkyrienskies.core.game.ships.ShipObject

/**
 * Is a kotlin friendly version of ShipOwnedBlockEntity
 */
interface KtShipBlockEntity : ShipOwnedBlockEntity {
    var ship: ShipObject

    override fun setOwner(newPos: BlockPos, newLevel: Level, newShip: ShipObject) {
        ship = newShip
    }
}
