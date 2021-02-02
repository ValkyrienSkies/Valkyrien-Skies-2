package org.valkyrienskies.mod.item

import org.valkyrienskies.mod.IShipObjectWorldProvider
import org.valkyrienskies.mod.JOMLConversion
import org.valkyrienskies.mod.VSNetworking
import org.valkyrienskies.mod.networking.impl.VSPacketShipDataList
import net.minecraft.server.level.ServerPlayer
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

                // Send the ShipData to the player
                val shipDataPacket = VSPacketShipDataList.createVSPacketShipDataList(listOf(shipData))
                VSNetworking.shipDataPacketToClientSender.sendToClient(shipDataPacket, player as ServerPlayer)

                // TODO: Create the initial ship chunks, transfer blocks, send ship to players, etc.
            }
        }

        return super.useOn(useOnContext)
    }
}