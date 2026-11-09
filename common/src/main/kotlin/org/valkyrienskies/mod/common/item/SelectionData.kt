package org.valkyrienskies.mod.common.item

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/** A copy on read; assign the edited tag to persist and synchronize a tool selection. */
internal var ItemStack.vsSelectionTag: CompoundTag?
    get() = get(DataComponents.CUSTOM_DATA)?.copyTag()
    set(value) {
        if (value == null || value.isEmpty) remove(DataComponents.CUSTOM_DATA)
        else set(DataComponents.CUSTOM_DATA, CustomData.of(value))
    }
