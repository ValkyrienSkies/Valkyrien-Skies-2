package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.compat.LoadedMods
import org.valkyrienskies.mod.compat.create.CreateMovedBlockEntities

object MovedBlockEntityTags {

    fun clearBeforeRemoval(entity: BlockEntity) {
        if (LoadedMods.create) CreateMovedBlockEntities.detachKinetics(entity)
        entity.load(CompoundTag())
    }

    fun loadDeferred(entity: BlockEntity, tag: CompoundTag) {
        if (LoadedMods.create) CreateMovedBlockEntities.loadDeferred(entity, tag) else entity.load(tag)
    }

    @JvmStatic
    fun relocate(state: BlockState, tag: CompoundTag, moved: ((BlockPos) -> BlockPos)? = null) {
        if (LoadedMods.create) CreateMovedBlockEntities.relocate(state, tag, moved)
    }
}
