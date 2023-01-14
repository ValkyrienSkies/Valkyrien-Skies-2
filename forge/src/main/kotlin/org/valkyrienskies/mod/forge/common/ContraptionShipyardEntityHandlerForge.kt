package org.valkyrienskies.mod.forge.common

import net.minecraft.world.entity.Entity
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.entity.handling.AbstractShipyardEntityHandler

object ContraptionShipyardEntityHandlerForge: AbstractShipyardEntityHandler() {
    override fun freshEntityInShipyard(entity: Entity, ship: Ship) {
        // TODO: Handle contraptions here
    }

    override fun entityRemovedFromShipyard(entity: Entity, ship: Ship) {
        // TODO: Handle contraptions here
    }
}
