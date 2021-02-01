package net.examplemod.item

import net.examplemod.IShipObjectWorldProvider
import net.examplemod.JOMLConversion
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3i

class ShipCreatorItem(properties: Properties) : Item(properties) {

    override fun useOn(useOnContext: UseOnContext?): InteractionResult {
        val player = useOnContext!!.player
        val level = useOnContext.level
        val blockPos = useOnContext.clickedPos
        val blockState: BlockState = level.getBlockState(blockPos)

        println("Player right clicked on $blockPos")

        if (!level.isClientSide()) {
            if (!blockState.isAir) {
                // Make a ship
                level as IShipObjectWorldProvider
                val shipData =
                    level.shipObjectWorld.createNewShipAtBlock(
                        JOMLConversion.convertBlockPosToVector3i(blockPos),
                        false
                    )

                val centerPos = JOMLConversion.convertVector3icToBlockPos(
                    shipData.chunkClaim.getCenterBlockCoordinates(
                        Vector3i()
                    )
                )

                // Move the block from the world to a ship
                level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 11)
                level.setBlock(centerPos, blockState, 11)

                // TODO: Create the initial ship chunks, transfer blocks, send ship to players, etc.
            }
        }

        return super.useOn(useOnContext)
    }
}