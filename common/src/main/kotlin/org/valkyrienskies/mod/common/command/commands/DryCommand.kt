package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlockContainer
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.forEach

/**
 * This object is used to register the `/vs dry` command,
 * but you may find its [dryShip] function useful elsewhere.
 */
object DryCommand {
    private const val DRY_SHIP_NO_BLOCKS_MESSAGE = "command.valkyrienskies.dry.no_blocks"
    private const val DRY_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.dry.success"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("dry")
            .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.dryShipCommandPerms)}
            .then(argument("ship", ShipArgument.ships())
                .executes {
                    dryShip(it, ShipArgument.getShip(it, "ship"))
                }
            )
        )
    }

    fun dryShip(context: CommandContext<CommandSourceStack>, ship: Ship): Int {
        val level = context.source.level

        // Shouldn't ever happen, but just in case
        if (ship.shipAABB == null) {
            context.source.sendFailure(Component.translatable(DRY_SHIP_NO_BLOCKS_MESSAGE))
            return 0
        }

        var dryCount = 0

        ship.shipAABB!!.forEach { x, y, z ->
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)

            if (state.liquid()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                dryCount += 1
            } else {
                if (state.block is LiquidBlockContainer && state.block !is SimpleWaterloggedBlock) {
                    // Hack specifically for seagrass and nothing else
                    level.destroyBlock(pos, true);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    dryCount += 1
                } else {
                    if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                        level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), 2);
                        // This will go up even if the block doesn't currently contain water, but whatever
                        dryCount += 1
                    }
                }
            }
        }

        context.source.sendSuccess(
            {
                Component.translatable(DRY_SHIP_SUCCESS_MESSAGE, dryCount)
            }, true
        )

        return 1
    }
}
