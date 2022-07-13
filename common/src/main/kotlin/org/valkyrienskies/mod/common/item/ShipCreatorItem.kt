package org.valkyrienskies.mod.common.item

import net.minecraft.network.chat.TextComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3i
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.relocateBlock
import org.valkyrienskies.mod.common.util.toBlockPos
import org.valkyrienskies.mod.common.util.toJOML

class ShipCreatorItem(properties: Properties, private val scale: Double) : Item(properties) {

    override fun useOn(ctx: UseOnContext): InteractionResult {
        println(ctx.level.isClientSide)
        val level = ctx.level as? ServerLevel ?: return super.useOn(ctx)
        val blockPos = ctx.clickedPos
        val blockState: BlockState = level.getBlockState(blockPos)

        println("Player right clicked on $blockPos")

        if (!level.isClientSide) {
            if (ChunkAllocator.isChunkInShipyard(blockPos.x shr 4, blockPos.z shr 4)) {
                ctx.player?.sendMessage(TextComponent("That chunk is already part of a ship!"), null)
            } else if (!blockState.isAir) {
                // Make a ship
                val dimensionId = level.dimensionId
                val shipData = level.shipObjectWorld.createNewShipAtBlock(blockPos.toJOML(), false, scale, dimensionId)

                val centerPos = shipData.chunkClaim.getCenterBlockCoordinates(Vector3i()).toBlockPos()

                // Move the block from the world to a ship
                level.relocateBlock(blockPos, centerPos, shipData)
            }
        }

        return super.useOn(ctx)
    }
}
