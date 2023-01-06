package org.valkyrienskies.mod.common.item

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.impl.datastructures.DenseBlockPosSet
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.isChunkInShipyard

class ShipAssemblerItem(properties: Properties) : Item(properties) {

    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)
        val pos = ctx.clickedPos
        val blockState: BlockState = level.getBlockState(pos)

        if (!level.isClientSide) {
            if (ctx.level.isChunkInShipyard(pos.x shr 4, pos.z shr 4)) {
                ctx.player?.sendSystemMessage(Component.literal("That chunk is already part of a ship!"))
            } else if (!blockState.isAir) {
                // Make a ship
                val set = DenseBlockPosSet()
                for (x in -3..3) {
                    for (z in -3..3) {
                        set.add(pos.x + x, pos.y, pos.z + z)
                    }
                }

                val shipData = createNewShipWithBlocks(pos, set, level)

                ctx.player?.sendSystemMessage(Component.literal("SHIPIFIED!"))
            }
        }

        return super.useOn(ctx)
    }
}
