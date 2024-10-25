package org.valkyrienskies.mod.common.item

import net.minecraft.Util
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.TextComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.joml.primitives.AABBi
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import java.util.function.DoubleSupplier

class AreaAssemblerItem(
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

        if (item.item !is AreaAssemblerItem) {
            return InteractionResult.FAIL
        }

        if (!level.isClientSide) {
            if (!blockState.isAir) {
                // Make a ship
                val dimensionId = level.dimensionId

                if (item.tag != null && item.tag!!.contains("firstPosX")) {
                    val firstPosX = item.tag!!.getInt("firstPosX")
                    val firstPosY = item.tag!!.getInt("firstPosY")
                    val firstPosZ = item.tag!!.getInt("firstPosZ")
                    if (level.shipObjectWorld.isBlockInShipyard(blockPos.x, blockPos.y, blockPos.z, dimensionId) != level.shipObjectWorld.isBlockInShipyard(firstPosX, firstPosY, firstPosZ, dimensionId)) {
                        ctx.player?.sendMessage(TextComponent("Cannot assemble between ship and world!"), Util.NIL_UUID)
                    } else if (level.getShipObjectManagingPos(blockPos) != level.getShipObjectManagingPos(Vec3i(firstPosX, firstPosY, firstPosZ))) {
                        ctx.player?.sendMessage(TextComponent("Cannot assemble something between two ships!"), Util.NIL_UUID)
                    } else {
                        val blockAABB = AABBi(blockPos.toJOML(), Vec3i(firstPosX, firstPosY, firstPosZ).toJOML())
                        blockAABB.correctBounds()
                        val blocks = ArrayList<BlockPos>()

                        for (x in blockAABB.minX..blockAABB.maxX) {
                            for (y in blockAABB.minY..blockAABB.maxY) {
                                for (z in blockAABB.minZ..blockAABB.maxZ) {
                                    if (level.getBlockState(BlockPos(x, y, z)).isAir) {
                                        continue
                                    }
                                    blocks.add(BlockPos(x, y, z))
                                }
                            }
                        }
                        ctx.player?.sendMessage(TextComponent("Assembling (${blockPos.x}, ${blockPos.y}, ${blockPos.z}) to ($firstPosX, $firstPosY, $firstPosZ)!"), Util.NIL_UUID)
                        ShipAssembler.assembleToShip(level, blocks, true, scale.asDouble)
                    }
                    item.tag!!.remove("firstPosX")
                    item.tag!!.remove("firstPosY")
                    item.tag!!.remove("firstPosZ")
                } else {
                    item.tag = item.orCreateTag.apply {
                        putInt("firstPosX", blockPos.x)
                        putInt("firstPosY", blockPos.y)
                        putInt("firstPosZ", blockPos.z)
                    }
                    ctx.player?.sendMessage(
                        TextComponent("First block selected: (${blockPos.x}, ${blockPos.y}, ${blockPos.z})"), Util.NIL_UUID)
                }
            }
        }

        return super.useOn(ctx)
    }
}
