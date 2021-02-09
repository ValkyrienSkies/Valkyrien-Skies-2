package org.valkyrienskies.mod.item

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import org.joml.Vector3i
import org.valkyrienskies.core.networking.impl.VSPacketShipDataList
import org.valkyrienskies.mod.IShipObjectWorldProvider
import org.valkyrienskies.mod.VSNetworking
import org.valkyrienskies.mod.util.toBlockPos
import org.valkyrienskies.mod.util.toJOML

class ShipCreatorItem(properties: Item.Settings) : Item(properties) {

    override fun useOnBlock(useOnContext: ItemUsageContext?): ActionResult {
        val player = useOnContext!!.player
        val level = useOnContext.world
        val blockPos = useOnContext.blockPos
        val blockState: BlockState = level.getBlockState(blockPos)

        println("Player right clicked on $blockPos")

        if (!level.isClient) {
            if (!blockState.isAir) {
                // Make a ship
                level as IShipObjectWorldProvider
                val shipData = level.shipObjectWorld.createNewShipAtBlock(blockPos.toJOML(), false)

                val centerPos = shipData.chunkClaim.getCenterBlockCoordinates(Vector3i()).toBlockPos()

                // Move the block from the world to a ship
                level.setBlockState(blockPos, Blocks.AIR.defaultState, 11)
                level.setBlockState(centerPos, blockState, 11)

                // Send the ShipData to the player
                val shipDataPacket = VSPacketShipDataList.createVSPacketShipDataList(listOf(shipData))
                VSNetworking.shipDataPacketToClientSender.sendToClient(shipDataPacket, player as ServerPlayerEntity)

                // TODO: Create the initial ship chunks, transfer blocks, send ship to players, etc.
            }
        }

        return super.useOnBlock(useOnContext)
    }
}