package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.TextComponent
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.apigame.ShipTeleportData
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl
import org.valkyrienskies.core.impl.game.ships.ShipData
import org.valkyrienskies.core.impl.game.ships.ShipObject
import org.valkyrienskies.core.impl.util.x
import org.valkyrienskies.core.impl.util.y
import org.valkyrienskies.core.impl.util.z
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
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
                .then(
                    literal("set-static").then(
                        argument("ships", ShipArgument.ships()).then(
                            argument("is-static", BoolArgumentType.bool()).executes {
                                try {
                                    val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                    val isStatic = BoolArgumentType.getBool(it, "is-static")
                                    r.forEach { ship ->
                                        if (ship is ShipObject) {
                                            // TODO: AAAAAAAAA THIS IS HORRIBLE how can the API support this?
                                            (ship.shipData as ShipData).isStatic = isStatic
                                        } else if (ship is ShipData) {
                                            // TODO: AAAAAAAAA THIS IS HORRIBLE how can the API support this?
                                            ship.isStatic = isStatic
                                        }

                                    }
                                    it.source.sendVSMessage(
                                        TextComponent("Set ${r.size} ships to be is-static=$isStatic!")
                                    )
                                    r.size
                                } catch (e: Exception) {
                                    if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                    throw e
                                }
                            })
                    )
                )
                .then(
                    literal("teleport").then(
                        argument("ships", ShipArgument.ships()).then(
                            argument("position", Vec3Argument.vec3()).executes {
                                // If only position is present then we execute this code
                                try {
                                    val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                    val position =
                                        Vec3Argument.getVec3(it as CommandContext<CommandSourceStack>, "position")
                                    val shipTeleportData: ShipTeleportData =
                                        ShipTeleportDataImpl(newPos = position.toJOML())
                                    r.forEach { ship ->
                                        vsCore.teleportShip(
                                            (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                            ship, shipTeleportData
                                        )
                                    }
                                    (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                        TextComponent("Teleported ${r.size} ships to $shipTeleportData!")
                                    )
                                    r.size
                                } catch (e: Exception) {
                                    if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                    throw e
                                }
                            }.then(
                                argument("euler-angles", RelativeVector3Argument.relativeVector3()).executes {
                                    // If only position is present then we execute this code
                                    try {
                                        val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                        val position =
                                            Vec3Argument.getVec3(it as CommandContext<CommandSourceStack>, "position")
                                        val eulerAngles =
                                            RelativeVector3Argument.getRelativeVector3(
                                                it as CommandContext<CommandSourceStack?>, "euler-angles"
                                            )

                                        val source = it.source as CommandSourceStack
                                        val shipTeleportData: ShipTeleportData =
                                            ShipTeleportDataImpl(
                                                newPos = position.toJOML(),
                                                newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                    source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                )
                                            )
                                        r.forEach { ship ->
                                            vsCore.teleportShip(
                                                (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                                ship, shipTeleportData
                                            )
                                        }
                                        (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                            TextComponent("Teleported ${r.size} ships to $shipTeleportData!")
                                        )
                                        r.size
                                    } catch (e: Exception) {
                                        if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                        throw e
                                    }
                                }.then(
                                    argument("velocity", RelativeVector3Argument.relativeVector3()).executes {
                                        // If only position is present then we execute this code
                                        try {
                                            val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                            val position =
                                                Vec3Argument.getVec3(
                                                    it as CommandContext<CommandSourceStack>, "position"
                                                )
                                            val eulerAngles =
                                                RelativeVector3Argument.getRelativeVector3(
                                                    it as CommandContext<CommandSourceStack?>, "euler-angles"
                                                )
                                            val velocity = RelativeVector3Argument.getRelativeVector3(
                                                it as CommandContext<CommandSourceStack?>, "velocity"
                                            )

                                            val source = it.source as CommandSourceStack
                                            val shipTeleportData: ShipTeleportData =
                                                ShipTeleportDataImpl(
                                                    newPos = position.toJOML(),
                                                    newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                        source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                    ),
                                                    newVel = velocity.toVector3d(0.0, 0.0, 0.0)
                                                )
                                            r.forEach { ship ->
                                                vsCore.teleportShip(
                                                    (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                                    ship, shipTeleportData
                                                )
                                            }
                                            (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                                TextComponent("Teleported ${r.size} ships to $shipTeleportData!")
                                            )
                                            r.size
                                        } catch (e: Exception) {
                                            if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                            throw e
                                        }
                                    }.then(
                                        argument(
                                            "angular-velocity", RelativeVector3Argument.relativeVector3()
                                        ).executes {
                                            // If only position is present then we execute this code
                                            try {
                                                val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                                val position =
                                                    Vec3Argument.getVec3(
                                                        it as CommandContext<CommandSourceStack>, "position"
                                                    )
                                                val eulerAngles =
                                                    RelativeVector3Argument.getRelativeVector3(
                                                        it as CommandContext<CommandSourceStack?>, "euler-angles"
                                                    )
                                                val velocity = RelativeVector3Argument.getRelativeVector3(
                                                    it as CommandContext<CommandSourceStack?>, "velocity"
                                                )
                                                val angularVelocity = RelativeVector3Argument.getRelativeVector3(
                                                    it as CommandContext<CommandSourceStack?>, "angular-velocity"
                                                )

                                                val source = it.source as CommandSourceStack
                                                val shipTeleportData: ShipTeleportData =
                                                    ShipTeleportDataImpl(
                                                        newPos = position.toJOML(),
                                                        newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                            source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                        ),
                                                        newVel = velocity.toVector3d(0.0, 0.0, 0.0),
                                                        newOmega = angularVelocity.toVector3d(0.0, 0.0, 0.0)
                                                    )
                                                r.forEach { ship ->
                                                    vsCore.teleportShip(
                                                        (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                                        ship, shipTeleportData
                                                    )
                                                }
                                                (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                                    TextComponent("Teleported ${r.size} ships to $shipTeleportData!")
                                                )
                                                r.size
                                            } catch (e: Exception) {
                                                if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                                throw e
                                            }
                                        }
                                    )
                                )
                            )
                        )
                    )
                )

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
                argument("ships", ShipArgument.selectorOnly()).executes {
                    val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                    val serverShip = serverShips.singleOrNull() ?: throw CommandRuntimeException(
                        TextComponent("Can only accept 1 ship as input")
                    )
                    val source = it.source as CommandSourceStack
                    val shipPos = serverShip.transform.positionInWorld

                    source.entity?.let { entity -> entity.teleportTo(shipPos.x, shipPos.y, shipPos.z); 1 } ?: 0
                }.then(
                    argument("entity", EntityArgument.entity()).executes {
                        val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        val entity = EntityArgument.getEntity(it as CommandContext<CommandSourceStack>, "entity")

                        serverShips.forEach { serverShip ->
                            vsCore.teleportShip(
                                it.source.shipWorld as ServerShipWorld, serverShip,
                                ShipTeleportDataImpl(newPos = Vector3d(entity.x, entity.y, entity.z))
                            )
                        }
                        (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                            TextComponent("Teleported ${serverShips.size} ships!")
                        )
                        serverShips.size
                    }
                ).then(
                    argument("pos", BlockPosArgument.blockPos()).executes {
                        val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        it as CommandContext<CommandSourceStack>
                        val pos = BlockPosArgument.getSpawnablePos(it, "pos")

                        serverShips.forEach { serverShip ->
                            vsCore.teleportShip(
                                it.source.shipWorld as ServerShipWorld, serverShip,
                                ShipTeleportDataImpl(newPos = pos.toJOMLD())
                            )
                        }
                        (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                            TextComponent("Teleported ${serverShips.size} ships!")
                        )
                        serverShips.size
                    }
                ).build()
            )

            getChild("targets").addChild(
                argument("ship", ShipArgument.selectorOnly()).executes {
                    val ship = ShipArgument.getShip(it, "ship")
                    it as CommandContext<CommandSourceStack>
                    val entities = EntityArgument.getEntities(it, "targets")
                    val shipPos = ship.transform.positionInWorld

                    entities.forEach { entity -> entity.teleportTo(shipPos.x, shipPos.y, shipPos.z) }

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
