package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.compat.LoadedMods
import org.valkyrienskies.mod.compat.create.CreateMovedBlockEntities

object MovedBlockEntityTags {

    @JvmStatic
    fun relocate(state: BlockState, tag: CompoundTag, moved: (BlockPos) -> BlockPos) {
        if (LoadedMods.create) CreateMovedBlockEntities.relocate(state, tag, moved)
    }
}
