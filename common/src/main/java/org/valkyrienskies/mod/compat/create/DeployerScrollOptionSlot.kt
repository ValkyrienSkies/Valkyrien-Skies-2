package org.valkyrienskies.mod.compat.create

import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import java.util.function.BiPredicate

class DeployerScrollOptionSlot(allowedDirections: BiPredicate<BlockState, Direction>) :
    DirectionalExtenderScrollOptionSlot(allowedDirections) {
    override fun getLocalOffset(level: LevelAccessor, pos: BlockPos, state: BlockState): Vec3 {
        return super.getLocalOffset(level, pos, state)
            .add(Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.FACING).normal).scale((-4 / 16f).toDouble()))
    }
}
