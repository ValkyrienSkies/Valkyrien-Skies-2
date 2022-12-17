package org.valkyrienskies.mod.common.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

class TestHingeBlockEntity(blockPos: BlockPos, blockState: BlockState) : BlockEntity(
    ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE, blockPos, blockState
) {
    fun tick() {
        println("Amogus")
    }
}
