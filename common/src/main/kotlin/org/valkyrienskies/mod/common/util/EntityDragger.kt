package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import org.valkyrienskies.core.game.IEntityProvider

class EntityDragger {
    companion object {
        /**
         * Drag these entities with the ship they're standing on.
         */
        fun dragEntitiesWithShips(entities: Iterable<Entity>) {
            entities.forEach { entity ->
                val vsEntity = (entity as IEntityProvider).vsEntity
                // TODO: Fill this in
            }
        }
    }
}
