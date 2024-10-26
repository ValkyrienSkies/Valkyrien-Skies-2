package org.valkyrienskies.mod.common.item

import net.minecraft.Util
import net.minecraft.network.chat.TextComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.function.DoubleSupplier

class ConnectionCheckerItem(
    properties: Properties, private val scale: DoubleSupplier, private val minScaling: DoubleSupplier
) : Item(properties) {

    override fun isFoil(stack: ItemStack): Boolean {
        return true
    }

    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)
        val blockPos = ctx.clickedPos
        val blockState: BlockState = level.getBlockState(blockPos)
        val item = ctx.itemInHand

        if (item.item !is ConnectionCheckerItem) {
            return InteractionResult.FAIL
        }

        if (!level.isClientSide) {
            val parentShip = ctx.level.getShipManagingPos(blockPos)
            if (!blockState.isAir) {
                // Make a ship
                val dimensionId = level.dimensionId

                if (parentShip != null) {
                    if (item.tag != null && item.tag!!.contains("firstPosX")) {
                        val firstPosX = item.tag!!.getInt("firstPosX")
                        val firstPosY = item.tag!!.getInt("firstPosY")
                        val firstPosZ = item.tag!!.getInt("firstPosZ")
                        val connected = level.shipObjectWorld.isConnectedBySolid(blockPos.x, blockPos.y, blockPos.z, firstPosX, firstPosY, firstPosZ, dimensionId)
                        ctx.player?.sendMessage(TextComponent("Connected: $connected"), Util.NIL_UUID)
                        item.tag!!.remove("firstPosX")
                        item.tag!!.remove("firstPosY")
                        item.tag!!.remove("firstPosZ")
                    } else {
                        item.tag = item.orCreateTag.apply {
                            putInt("firstPosX", blockPos.x)
                            putInt("firstPosY", blockPos.y)
                            putInt("firstPosZ", blockPos.z)
                        }
                        ctx.player?.sendMessage(TextComponent("First block selected: (${blockPos.x}, ${blockPos.y}, ${blockPos.z})"), Util.NIL_UUID)
                    }
                }
            }
        }

        return super.useOn(ctx)
    }
}
