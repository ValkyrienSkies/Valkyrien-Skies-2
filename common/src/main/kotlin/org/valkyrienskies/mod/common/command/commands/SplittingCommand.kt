package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment

object SplittingCommand {
    private const val SET_SPLITTING_MESSAGE = "command.valkyrienskies.set_splitting"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("splitting")
            .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.deleteShipCommandPerms)}
            .then(argument("ships", ShipArgument.ships())
                .then(argument("enable", BoolArgumentType.bool())
                    .executes {
                        val ships = ShipArgument.getShips(it, "ships")
                        val enable = BoolArgumentType.getBool(it, "enable")
                        ships.forEach { ship ->
                            if (ship is LoadedServerShip) {
                                ship.setAttachment(SplittingDisablerAttachment(enable))
                            }
                        }

                        if (ships.isEmpty()) return@executes 0

                        it.source.sendSuccess(
                            {
                                translatable(SET_SPLITTING_MESSAGE, enable, ships.size)
                            }, true
                        )

                        1
                    }
                )
            )
        )
    }
}
