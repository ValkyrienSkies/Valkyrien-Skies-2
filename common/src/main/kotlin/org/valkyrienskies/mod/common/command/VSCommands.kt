package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.game.VSCoreCommands
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource
import org.valkyrienskies.mod.util.logger

object VSCommands {
    private val LOGGER by logger()

    fun registerServerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("vs")
                .then(literal("delete").then(argument("ships", ShipArgument).executes {
                    try {
                        val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        VSCoreCommands.deleteShips(it.source.shipWorld as ServerShipWorld, r)

                        r.size
                    } catch (e: Exception) {
                        if (e !is CommandRuntimeException) LOGGER.throwing(e)
                        throw e
                    }
                }))

                // Single ship commands
                .then(
                    literal("ship").then(
                        argument("ship", ShipArgument)

                            // Delete a ship
                            .then(literal("delete").executes {

                                try {
                                    VSCoreCommands.deleteShips(
                                        it.source.shipWorld as ServerShipWorld,
                                        listOf(ShipArgument.getShip(it, "ship") as ServerShip)
                                    )
                                } catch (e: Exception) {
                                    if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                    throw e
                                }

                                1
                            })

                            // Rename a ship
                            .then(
                                literal("rename")
                                    .then(argument("newName", StringArgumentType.string())
                                        .executes {
                                            VSCoreCommands.renameShip(
                                                ShipArgument.getShip(it, "ship") as ServerShip,
                                                StringArgumentType.getString(it, "newName")
                                            )

                                            1
                                        })
                            )

                            // Scale a ship
                            .then(
                                literal("scale")
                                    .then(argument("newScale", FloatArgumentType.floatArg(0.001f))
                                        .executes {
                                            try {
                                                VSCoreCommands.scaleShip(
                                                    ShipArgument.getShip(it, "ship") as ServerShip,
                                                    FloatArgumentType.getFloat(it, "newScale")
                                                )
                                            } catch (e: Exception) {
                                                if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                                throw e
                                            }

                                            1
                                        })
                            )
                    )
                )
        )
    }

    fun registerClientCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // TODO implement client commands
    }
}

val CommandSourceStack.shipWorld: ShipWorld
    get() = (this as VSCommandSource).shipWorld
