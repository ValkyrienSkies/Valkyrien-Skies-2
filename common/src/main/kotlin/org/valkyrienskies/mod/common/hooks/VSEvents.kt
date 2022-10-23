package org.valkyrienskies.mod.common.hooks

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.core.util.events.EventEmitter
import org.valkyrienskies.core.util.events.EventEmitterImpl

object VSEvents {
    internal val shipBlockChangeEvent = EventEmitterImpl<ShipBlockChangeEvent>()

    data class ShipBlockChangeEvent(
        val ship: Ship,
        val pos: BlockPos,
        val oldState: BlockState,
        val newState: BlockState,
        val level: Level
    ) {
        companion object : EventEmitter<ShipBlockChangeEvent> by shipBlockChangeEvent
    }
}
