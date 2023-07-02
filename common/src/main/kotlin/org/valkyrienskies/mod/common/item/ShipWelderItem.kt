package org.valkyrienskies.mod.common.item

import net.minecraft.Util
import net.minecraft.network.chat.TextComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResult.FAIL
import net.minecraft.world.InteractionResult.SUCCESS
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.util.toBlockPos
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.util.ShipSplitter

class ShipWelderItem(properties: Properties) : Item(properties){

    private var fuseFrom: Vector3i? = null
    private var fuseTo: Vector3i? = null


    override fun isFoil(stack: ItemStack): Boolean {
        return true
    }

    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)
        if (level.getBlockState(ctx.clickedPos).isAir) {
            return super.useOn(ctx)
        }
        if (fuseFrom == null) {
            fuseFrom = ctx.clickedPos.toJOML()
            if (level.getShipObjectManagingPos(ctx.clickedPos) == null) {
                ctx.player?.sendMessage(TextComponent("Position is not on a ship!"), Util.NIL_UUID)
                return FAIL
            }
            ctx.player?.sendMessage(TextComponent("Started welding; Ship to weld selected at: ${fuseFrom}"), Util.NIL_UUID)
            return SUCCESS
        } else if (fuseTo == null) {
            fuseTo = ctx.clickedPos.relative(ctx.clickedFace, 1).toJOML()
            if (level.getShipObjectManagingPos(ctx.clickedPos) == null) {
                ctx.player?.sendMessage(TextComponent("Position is not on a ship! Clearing."), Util.NIL_UUID)
                fuseTo = null
                fuseFrom = null
                return FAIL
            }

            ctx.player?.sendMessage(TextComponent("Weld to position selected: ${fuseTo}, attempting weld."), Util.NIL_UUID)
            if (level.getShipObjectManagingPos(fuseFrom!!.toBlockPos()) == null) {
                ctx.player?.sendMessage(TextComponent("First position is not on a ship anymore? Clearing."), Util.NIL_UUID)
                return FAIL
            }
            val fusingShip : LoadedServerShip = level.getShipObjectManagingPos(fuseFrom!!.toBlockPos())!!
            val baseShip : LoadedServerShip = level.getShipObjectManagingPos(ctx.clickedPos)!!

            var success = false
            if (fuseTo != null && fuseFrom != null) {
                success = ShipSplitter.fuseShips(level, baseShip, fusingShip, fuseTo!!, fuseFrom!!)
            }

            fuseTo = null
            fuseFrom = null

            if (success) {
                ctx.player?.sendMessage(TextComponent("Weld successful!"), Util.NIL_UUID)
            } else {
                ctx.player?.sendMessage(TextComponent("Weld failed! How did we get here?"), Util.NIL_UUID)
                return FAIL
            }

            return SUCCESS
        }
        return super.useOn(ctx)
    }
}
