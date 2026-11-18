package org.valkyrienskies.mod.compat.create

import com.simibubi.create.content.kinetics.base.IRotate
import com.simibubi.create.content.kinetics.belt.BeltBlock
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.world.level.block.state.BlockState

/** Only call when Create is loaded. */
internal object CreateMovedBlockEntities {

    fun relocate(state: BlockState, tag: CompoundTag, moved: (BlockPos) -> BlockPos) {
        if (state.block !is IRotate) return

        tag.remove("Speed")
        tag.remove("Source")
        tag.remove("Network")

        // Belt segments point at their controller by absolute position.
        if (state.block is BeltBlock && tag.contains("Controller", Tag.TAG_COMPOUND.toInt())) {
            val controller = NbtUtils.readBlockPos(tag.getCompound("Controller"))
            tag.put("Controller", NbtUtils.writeBlockPos(moved(controller)))
        }
    }
}
