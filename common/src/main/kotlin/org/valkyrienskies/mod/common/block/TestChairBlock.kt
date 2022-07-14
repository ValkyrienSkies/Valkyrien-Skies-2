package org.valkyrienskies.mod.common.block

import net.minecraft.core.Direction
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.material.Material

class TestChairBlock :
    HorizontalDirectionalBlock(
        Properties.of(Material.METAL).strength(5.0f, 1200.0f).sound(SoundType.ANVIL)
    ) {
    init {
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH))
    }
}
