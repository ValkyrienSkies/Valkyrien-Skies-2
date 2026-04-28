package org.valkyrienskies.mod.common.item

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import org.joml.primitives.AABBi
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet
import org.valkyrienskies.mod.api.toJOMLd
import org.valkyrienskies.mod.api.toMinecraft
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.assembly.createNewShipWithBlocks
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import java.util.function.DoubleSupplier

class AreaAssemblerItem(
    properties: Properties, private val scale: DoubleSupplier, private val minScaling: DoubleSupplier, private val classicAssembler: Boolean = false
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
                        ctx.player?.sendSystemMessage(Component.literal("Cannot assemble between ship and world!"))
                    } else if (level.getLoadedShipManagingPos(blockPos) != level.getLoadedShipManagingPos(Vec3i(firstPosX, firstPosY, firstPosZ))) {
                        ctx.player?.sendSystemMessage(Component.literal("Cannot assemble something between two ships!"))
                    } else {
                        val blockAABB = AABBi(blockPos.toJOML(), Vec3i(firstPosX, firstPosY, firstPosZ).toJOML())
                        blockAABB.correctBounds()
                        val lowerCorner = BlockPos(blockAABB.minX, blockAABB.minY, blockAABB.minZ)
                        val upperCorner = BlockPos(blockAABB.maxX, blockAABB.maxY, blockAABB.maxZ)

                        val ship = if (classicAssembler) {
                            val denseSet = DenseBlockPosSet()
                            BlockPos.betweenClosed(lowerCorner, upperCorner).forEach{ denseSet.add(it.toJOML()) }
                            val center = lowerCorner.toJOMLd().add(upperCorner.toJOMLd()).div(2.0)
                            createNewShipWithBlocks(BlockPos.containing(center.toMinecraft()), denseSet, level)
                        }
                        else {
                            ShipAssembler.assembleToShip(level, BlockPos.betweenClosed(lowerCorner, upperCorner).map{ it.mutable() }.toSet(), 1.0)
                        }

                        ctx.player?.sendSystemMessage(Component.translatable("command.valkyrienskies.shipify.success_one", ship.slug))

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
                    ctx.player?.sendSystemMessage(
                        Component.literal("First block selected: (${blockPos.x}, ${blockPos.y}, ${blockPos.z})"))
                }
            }
        }

        return super.useOn(ctx)
    }
}
