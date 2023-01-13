package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.ships.Wing

interface WingBlock {
    fun getWing(level: Level, pos: BlockPos, blockState: BlockState): Wing
}
