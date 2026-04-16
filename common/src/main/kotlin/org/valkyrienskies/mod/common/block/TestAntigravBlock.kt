package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener
import org.valkyrienskies.mod.common.blockentity.TestAntigravBlockEntity

object TestAntigravBlock: Block(Properties.of().strength(1.0f, 120.0f).sound(SoundType.STONE)), EntityBlock {

    override fun newBlockEntity(
        blockPos: BlockPos, blockState: BlockState
    ): BlockEntity? {
        return TestAntigravBlockEntity(blockPos, blockState)
    }
}
