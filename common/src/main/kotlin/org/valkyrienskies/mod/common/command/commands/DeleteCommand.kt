package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig

object DeleteCommand {
    private const val DELETED_SHIPS_MESSAGE = "command.valkyrienskies.delete.success"
    private const val DELETED_ONE_SHIP_MESSAGE = "command.valkyrienskies.delete.success_one"
    private const val GET_SHIP_FAIL_MESSAGE = "command.valkyrienskies.get_ship.fail"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("delete")
            .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.deleteShipCommandPerms)}
            .then(argument("ships", ShipArgument.ships())
                .executes {
                    deleteShip(it, ShipArgument.getShips(it, "ships").toList() as List<ServerShip>)
                }.then(argument("deleteBlocks", BoolArgumentType.bool())
                    .executes {
                        deleteShip(it, ShipArgument.getShips(it, "ships").toList() as List<ServerShip>, BoolArgumentType.getBool(it, "deleteBlocks"))
                    }
                )
            )
        )
    }

    fun deleteShip(context: CommandContext<CommandSourceStack>, r: List<ServerShip>, deleteBlocks: Boolean = false): Int {
        val deletedShips = r.map {
                ship -> ShipAssembler.deleteShip(context.source.level, ship, deleteBlocks, dropBlocks = false)
        }

        if (deletedShips.sum() == 0) {
            context.source.sendFailure(Component.translatable(GET_SHIP_FAIL_MESSAGE))
            return 0
        }
        if (deletedShips.sum() == 1) {
            context.source.sendSuccess(
                {
                    Component.translatable(DELETED_ONE_SHIP_MESSAGE, r[0].slug)
                }, true
            )
        } else {
            context.source.sendSuccess(
                {
                    Component.translatable(DELETED_SHIPS_MESSAGE, r.size)
                }, true
            )
        }

        return r.size
    }
}
