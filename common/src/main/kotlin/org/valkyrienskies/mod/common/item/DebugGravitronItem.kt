package org.valkyrienskies.mod.common.item

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos

class DebugGravitronItem(properties: Properties) : VSItem(properties.stacksTo(1)) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (player is ServerPlayer) DebugGravitron.rightClick(player, hand)
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player as? ServerPlayer
        if (player != null) DebugGravitron.rightClick(player, context.hand)
        return InteractionResult.sidedSuccess(context.level.isClientSide)
    }

    override fun canAttackBlock(state: BlockState, level: Level, pos: BlockPos, player: Player) = false

    override fun appendHoverText(stack: ItemStack, level: Level?, list: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, level, list, flag)
        list.add(Component.translatable("item.valkyrienskies.debug_gravitron.tooltip"))
    }
}
