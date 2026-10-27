package org.valkyrienskies.mod.common.item

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.common.assembly.ShipWelder
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld

@OptIn(GameTickOnly::class)
class ShipWelderItem(properties: Properties) : VSItem(properties.stacksTo(1)) {
    override fun useOn(ctx: UseOnContext): InteractionResult {
        if (ctx.level.isClientSide) return InteractionResult.SUCCESS
        val level = ctx.level as? ServerLevel ?: return InteractionResult.PASS
        val player = ctx.player ?: return InteractionResult.PASS
        val stack = ctx.itemInHand
        fun message(suffix: String) { player.sendSystemMessage(Component.translatable("item.valkyrienskies.ship_welder.$suffix")) }
        if (player.isShiftKeyDown) {
            stack.removeTagKey(SELECTION)
            if (!ShipWelder.cancel(player.uuid)) message("cleared")
            return InteractionResult.CONSUME
        }
        val ship = level.getLoadedShipManagingPos(ctx.clickedPos)
        if (ship == null || level.getBlockState(ctx.clickedPos).isAir) {
            message("not_ship")
            return InteractionResult.CONSUME
        }
        val selection = stack.tag?.getCompound(SELECTION)
        if (selection == null || selection.isEmpty) {
            stack.getOrCreateTag().put(SELECTION, CompoundTag().apply {
                putString("dimension", level.dimension().location().toString())
                putLong("ship", ship.id)
                putLong("pos", ctx.clickedPos.asLong())
                putString("face", ctx.clickedFace.name)
            })
            message("selected")
            return InteractionResult.CONSUME
        }
        val target = level.shipObjectWorld.loadedShips.getById(selection.getLong("ship"))
        val targetPos = BlockPos.of(selection.getLong("pos"))
        val face = Direction.values().firstOrNull { it.name == selection.getString("face") }
        if (selection.getString("dimension") != level.dimension().location().toString() || target == null || face == null ||
            level.getLoadedShipManagingPos(targetPos)?.id != target.id) {
            stack.removeTagKey(SELECTION)
            message("stale")
            return InteractionResult.CONSUME
        }
        val refusal = ShipWelder.start(level, player.uuid, target, targetPos, face, ship, ctx.clickedPos, ctx.clickedFace)
        if (refusal == null) stack.removeTagKey(SELECTION)
        message(refusal ?: "pulling")
        return InteractionResult.CONSUME
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            if (!level.isClientSide) {
                stack.removeTagKey(SELECTION)
                if (!ShipWelder.cancel(player.uuid)) player.sendSystemMessage(Component.translatable("item.valkyrienskies.ship_welder.cleared"))
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
        return InteractionResultHolder.pass(stack)
    }

    override fun appendHoverText(stack: ItemStack, level: Level?, list: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, level, list, flag)
        list.add(Component.translatable("item.valkyrienskies.ship_welder.tooltip"))
        if (stack.tag?.contains(SELECTION) == true) list.add(Component.translatable("item.valkyrienskies.ship_welder.selected"))
    }

    companion object { private const val SELECTION = "vs_welder_selection" }
}
