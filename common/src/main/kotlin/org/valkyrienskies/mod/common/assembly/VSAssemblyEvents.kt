package org.valkyrienskies.mod.common.assembly

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.util.events.EventEmitterImpl

//TODO refine
/**
 * Should be used in both assembly and schematics
 */
object VSAssemblyEvents {
    val onCopy = EventEmitterImpl<OnCopy>()
    val onPasteBeforeBlocksAreLoaded = EventEmitterImpl<OnPasteBeforeBlocksAreCopied>()
    val onPasteAfterBlocksAreLoaded = EventEmitterImpl<OnPasteAfterBlocksAreLoaded>()
    val pasteSurvivalCost = EventEmitterImpl<PasteSurvivalCost>()

    /**
     * Should be called on schematic copy, before blocks were copied.
     * Custom save data should be added manually to [saveData].
     */
    data class OnCopy(
        val level: ServerLevel,
        val shipsToBeSaved: List<ServerShip>,
        val centerPositions: Map<ShipId, Vector3d>,
        val saveData: MutableMap<String, CompoundTag>
    )

    /**
     * Should be called after each individual ship is created, but before blocks being placed.
     * [partiallyLoadedShips] is a map of old shipId to a created ship. May not contain all ships.
     * [centerPositions] is a map of old shipId to a pair of previous ship center, and new ship center.
     */
    data class OnPasteBeforeBlocksAreCopied(
        val level: ServerLevel,
        val partiallyLoadedShips: Map<ShipId, ServerShip>,
        val newShip: Pair<ShipId, ServerShip>,
        val centerPositions: Map<ShipId, Pair<Vector3d, Vector3d>>,
        val saveData: Map<String, CompoundTag>
    )

    /**
     * Should be called after all ships are created with their blocks placed and block entities loaded
     * [loadedShips] is a map of old shipId to a new ship.
     * [centerPositions] is a map of old shipId to a pair of previous ship center, and new ship center.
     */
    data class OnPasteAfterBlocksAreLoaded(
        val level: ServerLevel,
        val loadedShips: Map<ShipId, ServerShip>,
        val centerPositions: Map<ShipId, Pair<Vector3d, Vector3d>>,
        val saveData: Map<String, CompoundTag>
    )

    data class PasteSurvivalCost(val saveData: Map<String, CompoundTag>, val addCost: (Map<Item, Int>) -> Unit)
}
