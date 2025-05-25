package org.valkyrienskies.mod.common.item

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.BLOCK_POS_COMPONENT
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
                    val firstPos: BlockPos? = item.get(BLOCK_POS_COMPONENT)
                    if (firstPos != null) {
                        val firstPosX = firstPos.x
                        val firstPosY = firstPos.y
                        val firstPosZ = firstPos.z
                        val connected = level.shipObjectWorld.isConnectedBySolid(blockPos.x, blockPos.y, blockPos.z, firstPosX, firstPosY, firstPosZ, dimensionId)
                        ctx.player?.sendSystemMessage(Component.translatable("Connected: $connected"))
                        item.remove(BLOCK_POS_COMPONENT)
                    } else {
                        item.set(BLOCK_POS_COMPONENT, blockPos)
                        ctx.player?.sendSystemMessage(
                            Component.translatable("First block selected: (${blockPos.x}, ${blockPos.y}, ${blockPos.z})"))
                    }
                }
            }
        }

        return super.useOn(ctx)
    }
}
