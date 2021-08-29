package org.valkyrienskies.mod.common.item

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import org.joml.Vector3i
import org.valkyrienskies.mod.common.BlockStateInfoProvider
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toBlockPos
import org.valkyrienskies.mod.common.util.toJOML

class ShipCreatorItem(properties: Settings, private val scale: Double) : Item(properties) {

    override fun useOnBlock(useOnContext: ItemUsageContext): ActionResult {
        println(useOnContext.world.isClient)
        val level = useOnContext.world as? ServerWorld ?: return super.useOnBlock(useOnContext)
        val blockPos = useOnContext.blockPos
        val blockState: BlockState = level.getBlockState(blockPos)

        println("Player right clicked on $blockPos")

        if (!level.isClient) {
            if (!blockState.isAir) {
                // Make a ship
                val shipData = level.shipObjectWorld.createNewShipAtBlock(blockPos.toJOML(), false, scale)

                val centerPos = shipData.chunkClaim.getCenterBlockCoordinates(Vector3i()).toBlockPos()

                // Move the block from the world to a ship
                level.setBlockState(blockPos, Blocks.AIR.defaultState, 11)
                level.setBlockState(centerPos, blockState, 11)

                BlockStateInfoProvider.onSetBlock(level, centerPos, Blocks.AIR.defaultState, blockState)
            }
        }

        return super.useOnBlock(useOnContext)
    }
}
