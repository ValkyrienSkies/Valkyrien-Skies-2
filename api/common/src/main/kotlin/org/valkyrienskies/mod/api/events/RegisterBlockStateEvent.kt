package org.valkyrienskies.mod.api.events

import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.SolidState

interface RegisterBlockStateEvent {

    fun newLiquidStateBuilder(): LiquidState.Builder
    fun buildLiquidState(block: LiquidState.Builder.() -> Unit): LiquidState
    fun newSolidStateBuilder(): SolidState.Builder
    fun buildSolidState(block: SolidState.Builder.() -> Unit): SolidState

    fun register(state: BlockState, solidState: SolidState)
    fun register(state: BlockState, liquidState: LiquidState)

    /**
     * Registers the Minecraft [state] to be represented by [LiquidState] and [SolidState] in the same block.
     * This is useful for e.g., a waterlogged chest, where the [liquidState] would be water and the [solidState] would
     * be the chest shape.
     */
    fun register(state: BlockState, liquidState: LiquidState, solidState: SolidState)
}
