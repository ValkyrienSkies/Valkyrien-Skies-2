package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.util.events.EventEmitterImpl

object VSAssemblyEvents {
    val beforeCopy = EventEmitterImpl<BeforeCopy>()
    val onPasteBeforeBlocksAreLoaded = EventEmitterImpl<OnPasteBeforeBlocksAreLoaded>()
    val onPasteAfterBlocksAreLoaded = EventEmitterImpl<OnPasteAfterBlocksAreLoaded>()

    /**
     * Should be called on schematic copy, before blocks were copied.
     * Tag saved in [tempData] will be passed to further events.
     */
    data class BeforeCopy(
        val level: ServerLevel,
        val minPos: Vector3dc,
        val maxPos: Vector3dc,
        val center: Vector3dc,
        val shipAssembledFrom: ServerShip?,
        val blockPositions: Set<BlockPos>,
        val tempData: MutableMap<String, CompoundTag>
    )

    /**
     * Should be called after each individual ship is created, but before blocks being placed.
     * [centerPosition] is a pair of previous ship center, and new ship center.
     */
    data class OnPasteBeforeBlocksAreLoaded(
        val level: ServerLevel,
        val oldShip: ServerShip?,
        val newShip: ServerShip?,
        val centerPosition: Pair<Vector3dc, Vector3dc>,
        val tempData: Map<String, CompoundTag>
    )

    /**
     * Should be called after all ships are created with their blocks placed and block entities loaded
     * [centerPosition] is a pair of previous ship center, and new ship center.
     */
    data class OnPasteAfterBlocksAreLoaded(
        val level: ServerLevel,
        val oldShip: ServerShip?,
        val newShip: ServerShip?,
        val centerPosition: Pair<Vector3dc, Vector3dc>,
        val tempData: Map<String, CompoundTag>
    )
}

object VSSchematicEvents {
    val onCopy = EventEmitterImpl<OnCopy>()
    val onPasteBeforeBlocksAreLoaded = EventEmitterImpl<OnPasteBeforeBlocksAreLoaded>()
    val onPasteAfterBlocksAreLoaded = EventEmitterImpl<OnPasteAfterBlocksAreLoaded>()
    val pasteSurvivalCost = EventEmitterImpl<PasteSurvivalCost>()

    /**
     * Should be called on schematic copy, before blocks were copied.
     * Custom save data should be added manually to [saveData].
     */
    data class OnCopy(
        val level: ServerLevel,
        val shipsToBeSaved: List<ServerShip>,
        val centerPositions: Map<ShipId, Vector3dc>,
        val saveData: MutableMap<String, CompoundTag>
    )

    /**
     * Should be called after each individual ship is created, but before blocks being placed.
     * [partiallyLoadedShips] is a map of old shipId to a created ship. May not contain all ships.
     * [centerPositions] is a map of old shipId to a pair of previous ship center, and new ship center.
     */
    data class OnPasteBeforeBlocksAreLoaded(
        val level: ServerLevel,
        val partiallyLoadedShips: Map<ShipId, ServerShip>,
        val newShip: Pair<ShipId, ServerShip>,
        val centerPositions: Map<ShipId, Pair<Vector3dc, Vector3dc>>,
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
        val centerPositions: Map<ShipId, Pair<Vector3dc, Vector3dc>>,
        val saveData: Map<String, CompoundTag>
    )

    data class PasteSurvivalCost(val saveData: Map<String, CompoundTag>, val addCost: (Map<Item, Int>) -> Unit)
}
