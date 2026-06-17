package org.valkyrienskies.mod.common.item

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

abstract class VSItem(properties: Properties): Item(properties) {
    override fun isFoil(stack: ItemStack): Boolean {
        return true
    }

    override fun appendHoverText(
        itemStack: ItemStack, level: Level?, list: MutableList<Component>, tooltipFlag: TooltipFlag
    ) {
        list.add(
            Component.translatable("item.valkyrienskies.debug_warn.one")
                .withStyle(ChatFormatting.RED)
                .withStyle(ChatFormatting.ITALIC)
        )
        list.add(
            Component.translatable("item.valkyrienskies.debug_warn.two")
                .withStyle(ChatFormatting.RED)
                .withStyle(ChatFormatting.ITALIC)
        )
    }

}
