package org.valkyrienskies.mod.common.item

import net.minecraft.ChatFormatting
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus
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

    override fun inventoryTick(item: ItemStack, level: Level, entity: Entity, i: Int, bl: Boolean) {
        super.inventoryTick(item, level, entity, i, bl)
        if (item.tag != null && item.tag!!.contains("firstPosX")) {
            val firstPosX = item.tag!!.getInt("firstPosX")
            val firstPosY = item.tag!!.getInt("firstPosY")
            val firstPosZ = item.tag!!.getInt("firstPosZ")

            if (level.isClientSide) {
                //spawn grey particles surrounding originally selected position
                for (x in -1..1) for (y in -1..1) for (z in -1..1) {
                    repeat (1) {
                        level.addParticle(
                            net.minecraft.core.particles.ParticleTypes.SMOKE,
                            true,
                            firstPosX + 0.5 + x * 0.5,
                            firstPosY + 0.5 + y * 0.5,
                            firstPosZ + 0.5 + z * 0.5,
                            0.0,
                            0.0,
                            0.0
                        )
                    }
                }
            }
        }
    }

    override fun useOn(ctx: UseOnContext): InteractionResult {
        val level = ctx.level ?: return super.useOn(ctx)
        val blockPos = ctx.clickedPos
        val blockState: BlockState = level.getBlockState(blockPos)
        val item = ctx.itemInHand

        if (item.item !is ConnectionCheckerItem) {
            return InteractionResult.FAIL
        }

        val parentShip = ctx.level.getShipManagingPos(blockPos)
        if (!blockState.isAir) {
            // Make a ship
            val dimensionId = level.dimensionId

            if (parentShip != null) {
                if (item.tag != null && item.tag!!.contains("firstPosX")) {
                    val firstPosX = item.tag!!.getInt("firstPosX")
                    val firstPosY = item.tag!!.getInt("firstPosY")
                    val firstPosZ = item.tag!!.getInt("firstPosZ")
                    val connected = level.shipObjectWorld.isConnectedBySolid(blockPos.x, blockPos.y, blockPos.z, firstPosX, firstPosY, firstPosZ, dimensionId)
                    val chatColor = when (connected) {
                        ConnectionStatus.CONNECTED -> ChatFormatting.GREEN
                        ConnectionStatus.DISCONNECTED -> ChatFormatting.RED
                        ConnectionStatus.UNKNOWN -> ChatFormatting.YELLOW
                    }
                    if (!level.isClientSide) {
                        ctx.player?.sendSystemMessage(Component.literal("[SERVERSIDE] ").withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal("Connected:").withStyle(
                            ChatFormatting.RESET)).append(Component.literal(" $connected").withStyle(chatColor)))
                    } else {
                        ctx.player?.sendSystemMessage(Component.literal("[CLIENTSIDE] ").withStyle(ChatFormatting.GOLD).append(Component.literal("Connected:").withStyle(
                            ChatFormatting.RESET)).append(Component.literal(" $connected").withStyle(chatColor)))
                        val color = when (connected) {
                            ConnectionStatus.CONNECTED -> Vector3f(0.0f, 1.0f, 0.0f) // Green
                            ConnectionStatus.DISCONNECTED -> Vector3f(1.0f, 0.0f, 0.0f) // Red
                            ConnectionStatus.UNKNOWN -> Vector3f(1.0f, 1.0f, 0.0f) // Yellow
                        }
                        for (x in -1..1) for (y in -1..1) for (z in -1..1) {
                            repeat (1) {
                                level.addParticle(
                                    DustParticleOptions(color, 1f),
                                    true,
                                    blockPos.x.toDouble() + 0.5 + x * 0.5,
                                    blockPos.y.toDouble() + 0.5 + y * 0.5,
                                    blockPos.z.toDouble() + 0.5 + z * 0.5,
                                    0.0,
                                    0.0,
                                    0.0
                                )
                            }
                        }
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
                    if (!level.isClientSide) {
                        ctx.player?.sendSystemMessage(
                            Component.literal("First block selected: (${blockPos.x}, ${blockPos.y}, ${blockPos.z})").withStyle(
                                ChatFormatting.ITALIC))
                    }
                }
            }
        }

        return super.useOn(ctx)
    }
}
