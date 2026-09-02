package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.ShipGravityAttachment

object GravityCommand {
    private const val SET_GRAVITY_MESSAGE = "command.valkyrienskies.set_gravity"
    private const val SET_GRAVITY_DEFAULT = "command.valkyrienskies.set_gravity.default"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("gravity")
            .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.setGravityCommandPerms)}
            .then(argument("ships", ShipArgument.ships())
                .then(argument("amount", DoubleArgumentType.doubleArg())
                    .executes {
                        val ships = ShipArgument.getShips(it, "ships")
                        val amount = DoubleArgumentType.getDouble(it, "amount")
                        ships.forEach { ship ->
                            if (ship is LoadedServerShip) {
                                ship.setAttachment(ShipGravityAttachment(amount))
                            }
                        }

                        if (ships.isEmpty()) return@executes 0

                        it.source.sendSuccess(
                            {
                                translatable(SET_GRAVITY_MESSAGE, ships.size, amount)
                            }, true
                        )

                        1
                    }
                ).then(literal("default")
                    .executes {
                        val ships = ShipArgument.getShips(it, "ships")
                        ships.forEach { ship ->
                            if (ship is LoadedServerShip) {
                                ship.setAttachment(ShipGravityAttachment())
                            }
                        }

                        if (ships.isEmpty()) return@executes 0

                        it.source.sendSuccess(
                            {
                                translatable(SET_GRAVITY_MESSAGE, ships.size, translatable(SET_GRAVITY_DEFAULT).string)
                            }, true
                        )

                        1
                    }
                )
            )
        )
    }
}
