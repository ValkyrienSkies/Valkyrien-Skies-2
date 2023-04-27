package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.network.chat.TextComponent
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.impl.util.x
import org.valkyrienskies.core.impl.util.y
import org.valkyrienskies.core.impl.util.z
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource
import org.valkyrienskies.mod.util.logger

object VSCommands {
    private val LOGGER by logger()

    private fun literal(name: String) =
        LiteralArgumentBuilder.literal<VSCommandSource>(name)

    private fun <T> argument(name: String, type: ArgumentType<T>) =
        RequiredArgumentBuilder.argument<VSCommandSource, T>(name, type)

    fun registerServerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher as CommandDispatcher<VSCommandSource>

        dispatcher.register(
            literal("vs")
                .then(literal("delete").then(argument("ships", ShipArgument.ships()).executes {
                    try {
                        val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        vsCore.deleteShips(it.source.shipWorld as ServerShipWorld, r)
                        it.source.sendVSMessage(TextComponent("Deleted ${r.size} ships!"))
                        r.size
                    } catch (e: Exception) {
                        if (e !is CommandRuntimeException) LOGGER.throwing(e)
                        throw e
                    }
                }))

                // Single ship commands
                .then(
                    literal("ship").then(
                        argument("ship", ShipArgument.ships())

                            // Delete a ship
                            .then(literal("delete").executes {
                                try {
                                    vsCore.deleteShips(
                                        it.source.shipWorld as ServerShipWorld,
                                        listOf(ShipArgument.getShip(it, "ship") as ServerShip)
                                    )
                                    1
                                } catch (e: Exception) {
                                    if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                    throw e
                                }
                            })

                            // Rename a ship
                            .then(
                                literal("rename")
                                    .then(argument("newName", StringArgumentType.string())
                                        .executes {
                                            try {
                                                vsCore.renameShip(
                                                    ShipArgument.getShip(it, "ship") as ServerShip,
                                                    StringArgumentType.getString(it, "newName")
                                                )
                                                1
                                            } catch (e: Exception) {
                                                if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                                throw e
                                            }
                                        })
                            )

                            /* DISABLED UNTIL VS-BODIES IS READY
                            // Scale a ship
                            .then(
                                literal("scale")
                                    .then(argument("newScale", FloatArgumentType.floatArg(0.001f))
                                        .executes {
                                            try {
                                                vsCore.scaleShip(
                                                    ShipArgument.getShip(it, "ship") as ServerShip,
                                                    FloatArgumentType.getFloat(it, "newScale")
                                                )
                                                1
                                            } catch (e: Exception) {
                                                if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                                throw e
                                            }
                                        })
                            )
                             */
                    )
                )
        )

        dispatcher.root.children.first { it.name == "teleport" }.apply {
            addChild(
                argument("ship", ShipArgument.selectorOnly()).executes {
                    val ship = ShipArgument.getShip(it, "ship")
                    val source = it.source as CommandSourceStack
                    val shipPos = ship.transform.positionInWorld

                    source.entity?.let { it.teleportTo(shipPos.x, shipPos.y, shipPos.z); 1 } ?: 0
                }.then(
                    argument("entity", EntityArgument.entity()).executes {
                        val ship = ShipArgument.getShip(it, "ship")
                        it as CommandContext<CommandSourceStack>
                        val entity = EntityArgument.getEntity(it, "entity")

                        vsCore.teleportShip(
                            it.source.shipWorld as ServerShipWorld, ship as ServerShip, entity.x, entity.y, entity.z
                        )

                        1
                    }
                ).then(
                    argument("pos", BlockPosArgument.blockPos()).executes {
                        val ship = ShipArgument.getShip(it, "ship")
                        it as CommandContext<CommandSourceStack>
                        val pos = BlockPosArgument.getSpawnablePos(it, "pos")

                        vsCore.teleportShip(
                            it.source.shipWorld as ServerShipWorld, ship as ServerShip, pos.x.toDouble(),
                            pos.y.toDouble(), pos.z.toDouble()
                        )

                        1
                    }
                ).build()
            )

            getChild("targets").addChild(
                argument("ship", ShipArgument.selectorOnly()).executes {
                    val ship = ShipArgument.getShip(it, "ship")
                    it as CommandContext<CommandSourceStack>
                    val entities = EntityArgument.getEntities(it, "targets")
                    val shipPos = ship.transform.positionInWorld

                    entities.forEach { it.teleportTo(shipPos.x, shipPos.y, shipPos.z) }

                    entities.size
                }.build()
            )
        }
    }

    fun registerClientCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // TODO implement client commands
    }
}

val CommandSourceStack.shipWorld: ShipWorld
    get() = (this as VSCommandSource).shipWorld
