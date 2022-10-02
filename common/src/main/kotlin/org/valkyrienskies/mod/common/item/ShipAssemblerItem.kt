package org.valkyrienskies.mod.common.item

import net.minecraft.Util
import net.minecraft.core.SectionPos
import net.minecraft.network.chat.TextComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.datastructures.ChunkSet
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.dimensionId
import java.util.TreeMap

class ShipAssemblerItem(properties: Properties) : Item(properties) {

    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)
        val pos = ctx.clickedPos
        val blockState: BlockState = level.getBlockState(pos)

        if (!level.isClientSide) {
            if (ChunkAllocator.isChunkInShipyard(pos.x shr 4, pos.z shr 4)) {
                ctx.player?.sendMessage(TextComponent("That chunk is already part of a ship!"), Util.NIL_UUID)
            } else if (!blockState.isAir) {
                // Make a ship
                val dimensionId = level.dimensionId
                val blocks = TreeMap<SectionPos, ChunkSet>()
                val set = ChunkSet(pos.x shr 4, pos.y shr 4, pos.z shr 4)
                blocks[SectionPos.of(pos.x shr 4, pos.y shr 4, pos.z shr 4)] = set
                for (x in -1..1) {
                    for (z in -1..1) {
                        set.setBlock((pos.x + x) and 15, pos.y and 15, (pos.z + z) and 15)
                    }
                }

                val shipData = createNewShipWithBlocks(pos, blocks, level)

                ctx.player?.sendMessage(TextComponent("SHIPIFIED!"), Util.NIL_UUID)
            }
        }

        return super.useOn(ctx)
    }
}
