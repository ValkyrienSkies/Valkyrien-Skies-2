package org.valkyrienskies.mod.common.block


import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import org.valkyrienskies.mod.api.PositionGravity
import org.valkyrienskies.mod.common.util.toJOMLD

object TestGravityBlock : Block(
    Properties.of(Material.METAL).strength(1.0f, 120.0f).sound(SoundType.METAL)
) {
    override fun onPlace(
        blockState: BlockState, level: Level, blockPos: BlockPos, blockState2: BlockState, bl: Boolean
    ) {
        super.onPlace(blockState, level, blockPos, blockState2, bl)
        PositionGravity().PositionGravity(blockPos.toJOMLD(), 580.0)
    }

    override fun onRemove(
        blockState: BlockState, level: Level, blockPos: BlockPos, blockState2: BlockState, bl: Boolean
    ) {
        super.onRemove(blockState, level, blockPos, blockState2, bl)
        PositionGravity().removePosition(blockPos.toJOMLD())
    }
    }
