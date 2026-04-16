package org.valkyrienskies.mod.common.item

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.getShipManagingPos

class ShipRemoverItem(properties: Properties) : Item(properties) {

    override fun isFoil(stack: ItemStack): Boolean {
        return true
    }

    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)
        val pos = ctx.clickedPos

        if (!level.isClientSide) {
            val ship = level.getShipManagingPos(pos)
            if (ship == null) {
                ctx.player?.sendSystemMessage(Component.translatable("command.valkyrienskies.get_ship.fail"))
            } else {
                ShipAssembler.deleteShip(level, ship, deleteBlocks = true, dropBlocks = ctx.player?.isShiftKeyDown == true)
                ctx.player?.sendSystemMessage(Component.translatable("command.valkyrienskies.delete.success_one", ship.slug))
            }
        }

        return super.useOn(ctx)
    }
}
