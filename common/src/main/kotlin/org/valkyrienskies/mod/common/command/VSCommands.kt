package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.commands.synchronization.ArgumentTypes
import net.minecraft.commands.synchronization.EmptyArgumentSerializer
import net.minecraft.network.chat.Component.translatable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.BlockHitResult
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.apigame.ShipTeleportData
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl
import org.valkyrienskies.core.util.x
import org.valkyrienskies.core.util.y
import org.valkyrienskies.core.util.z
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource
import org.valkyrienskies.mod.util.logger

object VSCommands {
    private val LOGGER by logger()
    private const val DELETED_SHIPS_MESSAGE = "command.valkyrienskies.delete.success"
    private const val SET_SHIP_STATIC_SUCCESS_MESSAGE = "command.valkyrienskies.set_static.success"
    private const val TELEPORT_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.teleport.success"
    private const val GET_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.get_ship.success"
    private const val GET_SHIP_FAIL_MESSAGE = "command.valkyrienskies.get_ship.fail"
    private const val GET_SHIP_ONLY_USABLE_BY_ENTITIES_MESSAGE = "command.valkyrienskies.get_ship.only_usable_by_entities"
    private const val TELEPORTED_MULTIPLE_SHIPS_SUCCESS = "command.valkyrienskies.teleport.multiple_ship_success"
    private const val TELEPORT_FIRST_ARG_CAN_ONLY_INPUT_1_SHIP = "command.valkyrienskies.mc_teleport.can_only_teleport_to_one_ship"
    private const val SCALED_SHIPS_MESSAGE = "command.valkyrienskies.scale.success"
    private const val REQUIRED_PERMISSION = 2

    fun bootstrap() {
        ArgumentTypes.register("valkyrienskies:ship",
            ShipArgument::class.java,
            ShipArgument.Serializer)

        ArgumentTypes.register("valkyrienskies:relative_vector",
            RelativeVector3Argument::class.java,
            EmptyArgumentSerializer(::RelativeVector3Argument))
    }

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
                        it.source.sendVSMessage(Component.translatable(DELETED_SHIPS_MESSAGE, r.size))
                        r.size
                    } catch (e: Exception) {
                        if (e !is CommandRuntimeException) LOGGER.throwing(e)
                        throw e
                    }
                })).requires { (it as CommandSourceStack).hasPermission(REQUIRED_PERMISSION) }
                .then(
                    literal("set-static").then(
                        argument("ships", ShipArgument.ships()).then(
                            argument("is-static", BoolArgumentType.bool()).executes {
                                try {
                                    val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                    val isStatic = BoolArgumentType.getBool(it, "is-static")
                                    r.forEach { ship -> ship.isStatic = isStatic }
                                    it.source.sendVSMessage(
                                        Component.translatable(
                                            SET_SHIP_STATIC_SUCCESS_MESSAGE, r.size, if (isStatic) "true" else "false"
                                        )
                                    )
                                    r.size
                                } catch (e: Exception) {
                                    if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                    throw e
                                }
                            })
                    )
                ).requires { (it as CommandSourceStack).hasPermission(REQUIRED_PERMISSION) }
                .then(
                    literal("teleport").then(
                        argument("ships", ShipArgument.ships()).then(
                            argument("position", Vec3Argument.vec3()).executes {
                                // If only position is present then we execute this code
                                try {
                                    val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                    val position =
                                        Vec3Argument.getVec3(it as CommandContext<CommandSourceStack>, "position")
                                    val dimensionId = (it.source as CommandSourceStack).level.dimensionId
                                    val shipTeleportData: ShipTeleportData =
                                        ShipTeleportDataImpl(newPos = position.toJOML(), newDimension = dimensionId)
                                    r.forEach { ship ->
                                        vsCore.teleportShip(
                                            (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                            ship, shipTeleportData
                                        )
                                    }
                                    (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                        Component.translatable(TELEPORT_SHIP_SUCCESS_MESSAGE, r.size, shipTeleportData.toString())
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
                                        val dimensionId = (it.source as CommandSourceStack).level.dimensionId
                                        val shipTeleportData: ShipTeleportData =
                                            ShipTeleportDataImpl(
                                                newPos = position.toJOML(),
                                                newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                    source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                ),
                                                newDimension = dimensionId,
                                            )
                                        r.forEach { ship ->
                                            vsCore.teleportShip(
                                                (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                                ship, shipTeleportData
                                            )
                                        }
                                        (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                            Component.translatable(TELEPORT_SHIP_SUCCESS_MESSAGE, r.size, shipTeleportData.toString())
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
                                            val dimensionId = (it.source as CommandSourceStack).level.dimensionId
                                            val shipTeleportData: ShipTeleportData =
                                                ShipTeleportDataImpl(
                                                    newPos = position.toJOML(),
                                                    newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                        source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                    ),
                                                    newVel = velocity.toVector3d(0.0, 0.0, 0.0),
                                                    newDimension = dimensionId,
                                                )
                                            r.forEach { ship ->
                                                vsCore.teleportShip(
                                                    (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                                    ship, shipTeleportData
                                                )
                                            }
                                            (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                                Component.translatable(TELEPORT_SHIP_SUCCESS_MESSAGE, r.size, shipTeleportData.toString())
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
                                                val dimensionId = (it.source as CommandSourceStack).level.dimensionId
                                                val shipTeleportData: ShipTeleportData =
                                                    ShipTeleportDataImpl(
                                                        newPos = position.toJOML(),
                                                        newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                            source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                        ),
                                                        newVel = velocity.toVector3d(0.0, 0.0, 0.0),
                                                        newOmega = angularVelocity.toVector3d(0.0, 0.0, 0.0),
                                                        newDimension = dimensionId,
                                                    )
                                                r.forEach { ship ->
                                                    vsCore.teleportShip(
                                                        (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld,
                                                        ship, shipTeleportData
                                                    )
                                                }
                                                (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                                                    Component.translatable(TELEPORT_SHIP_SUCCESS_MESSAGE, r.size, shipTeleportData.toString())
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
                ).requires { (it as CommandSourceStack).hasPermission(REQUIRED_PERMISSION) }
                .then(literal("get-ship").executes {
                    try {
                        val mcCommandContext = it as CommandContext<CommandSourceStack>

                        var success = false
                        val sourceEntity: Entity? = mcCommandContext.source.entity
                        if (sourceEntity != null) {
                            val rayTrace = sourceEntity.pick(10.0, 1.0.toFloat(), false)
                            if (rayTrace is BlockHitResult) {
                                val ship = sourceEntity.level.getShipManagingPos(rayTrace.blockPos)
                                if (ship != null) {
                                    (it.source as VSCommandSource).sendVSMessage(
                                        Component.translatable(GET_SHIP_SUCCESS_MESSAGE, ship.slug)
                                    )
                                    success = true
                                }
                            }
                            if (success) {
                                1
                            } else {
                                (it.source as VSCommandSource).sendVSMessage(Component.translatable(GET_SHIP_FAIL_MESSAGE))
                                0
                            }
                        } else {
                            (it.source as VSCommandSource).sendVSMessage(
                                Component.translatable(GET_SHIP_ONLY_USABLE_BY_ENTITIES_MESSAGE)
                            )
                            0
                        }
                    } catch (e: Exception) {
                        if (e !is CommandRuntimeException) LOGGER.throwing(e)
                        throw e
                    }
                })
                .then(
                    literal("scale").then(
                        argument("ships", ShipArgument.ships()).then(
                            argument("newScale", DoubleArgumentType.doubleArg(0.1, 10.0)).executes {
                                try {
                                    val world =
                                        (it as CommandContext<VSCommandSource>).source.shipWorld as ServerShipWorld
                                    val newScale = DoubleArgumentType.getDouble(it, "newScale")
                                    val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                    r.forEach { ship -> vsCore.scaleShip(world, ship, newScale) }
                                    it.source.sendVSMessage(Component.translatable(SCALED_SHIPS_MESSAGE, r.size))
                                    r.size
                                } catch (e: Exception) {
                                    if (e !is CommandRuntimeException) LOGGER.throwing(e)
                                    throw e
                                }
                            })
                    )
                ).requires { (it as CommandSourceStack).hasPermission(REQUIRED_PERMISSION) }
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
                    )
                ).requires { (it as CommandSourceStack).hasPermission(REQUIRED_PERMISSION) }
        )

        dispatcher.root.children.first { it.name == "teleport" }.apply {
            addChild(
                argument("ships", ShipArgument.selectorOnly()).executes {
                    val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                    val serverShip = serverShips.singleOrNull() ?: throw CommandRuntimeException(
                        Component.translatable(TELEPORT_FIRST_ARG_CAN_ONLY_INPUT_1_SHIP)
                    )
                    val source = it.source as CommandSourceStack
                    val shipPos = serverShip.transform.positionInWorld

                    source.entity?.let { entity -> entity.teleportTo(shipPos.x, shipPos.y, shipPos.z); 1 } ?: 0
                }.then(
                    argument("entity", EntityArgument.entity()).executes {
                        val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        val entity = EntityArgument.getEntity(it as CommandContext<CommandSourceStack>, "entity")

                        serverShips.forEach { serverShip ->
                            val dimensionId = (it.source as CommandSourceStack).level.dimensionId
                            vsCore.teleportShip(
                                it.source.shipWorld as ServerShipWorld, serverShip,
                                ShipTeleportDataImpl(newPos = Vector3d(entity.x, entity.y, entity.z), newDimension = dimensionId)
                            )
                        }
                        (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                            Component.translatable(TELEPORTED_MULTIPLE_SHIPS_SUCCESS, serverShips.size)
                        )
                        serverShips.size
                    }
                ).then(
                    argument("pos", BlockPosArgument.blockPos()).executes {
                        val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        it as CommandContext<CommandSourceStack>
                        val pos = BlockPosArgument.getSpawnablePos(it, "pos")

                        serverShips.forEach { serverShip ->
                            val dimensionId = (it.source as CommandSourceStack).level.dimensionId
                            vsCore.teleportShip(
                                it.source.shipWorld as ServerShipWorld, serverShip,
                                ShipTeleportDataImpl(newPos = pos.toJOMLD(), newDimension = dimensionId)
                            )
                        }
                        (it as CommandContext<VSCommandSource>).source.sendVSMessage(
                            Component.translatable(TELEPORTED_MULTIPLE_SHIPS_SUCCESS, serverShips.size)
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
