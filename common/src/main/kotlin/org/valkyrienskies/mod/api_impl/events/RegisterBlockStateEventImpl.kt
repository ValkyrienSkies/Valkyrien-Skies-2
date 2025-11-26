package org.valkyrienskies.mod.api_impl.events

import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.mod.api.events.RegisterBlockStateEvent
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.vsCore

class RegisterBlockStateEventImpl : RegisterBlockStateEvent {
    val toRegister = mutableListOf<Pair<BlockState, VsiBlockState>>()

    override fun newLiquidStateBuilder(): LiquidState.Builder =
        vsCore.newLiquidStateBuilder()

    override fun buildLiquidState(block: LiquidState.Builder.() -> Unit): LiquidState =
        vsCore.newLiquidStateBuilder().apply(block).build()

    override fun newSolidStateBuilder(): SolidState.Builder =
        vsCore.newSolidStateBuilder()

    override fun buildSolidState(block: SolidState.Builder.() -> Unit): SolidState =
        vsCore.newSolidStateBuilder().apply(block).build()

    override fun register(state: BlockState, solidState: SolidState) {
        toRegister.add(Pair(state, VsiBlockState(solidState, null)))
    }

    override fun register(state: BlockState, liquidState: LiquidState) {
        toRegister.add(Pair(state, VsiBlockState(null, liquidState)))
    }

    override fun register(state: BlockState, liquidState: LiquidState, solidState: SolidState) {
        toRegister.add(Pair(state, VsiBlockState(solidState, liquidState)))
    }
}
