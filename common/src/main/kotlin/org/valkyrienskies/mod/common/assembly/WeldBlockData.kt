package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag

internal object WeldBlockData {
    fun relocatedTag(original: CompoundTag, destination: BlockPos): CompoundTag = original.copy().apply {
        putInt("x", destination.x)
        putInt("y", destination.y)
        putInt("z", destination.z)
    }
}
