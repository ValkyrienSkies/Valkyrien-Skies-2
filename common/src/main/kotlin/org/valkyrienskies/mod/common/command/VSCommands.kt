package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.ForgeConfigSpec
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.impl.api_impl.config.ConfigPhysicsBackendType
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.core.internal.ShipTeleportData
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.config.VSConfigUpdater
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixin.feature.commands.ClientSuggestionProviderAccessor
import org.valkyrienskies.mod.util.logger
import java.text.NumberFormat
import kotlin.math.round
import kotlin.math.roundToInt

object VSCommands {
    private val LOGGER by logger()
    private const val DELETED_SHIPS_MESSAGE = "command.valkyrienskies.delete.success"
    private const val REMASSED_SHIPS_SUCCESS_MESSAGE = "command.valkyrienskies.remass.success"
    private const val REMASSED_SHIP_FAIL_MESSAGE = "command.valkyrienskies.remass.fail"
    private const val SET_SHIP_STATIC_SUCCESS_MESSAGE = "command.valkyrienskies.set_static.success"
    private const val TELEPORT_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.teleport.success"
    private const val DELETED_ONE_SHIP_MESSAGE = "command.valkyrienskies.delete.success_one"
    private const val REMASSED_ONE_SHIP_MESSAGE = "command.valkyrienskies.remass.success_one"
    private const val SET_ONE_SHIP_STATIC_SUCCESS_MESSAGE = "command.valkyrienskies.set_static.success_one"
    private const val TELEPORT_ONE_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.teleport.success_one"
    private const val LOD_CURRENT_MESSAGE = "command.valkyrienskies.lod.current"
    private const val LOD_SET_MESSAGE = "command.valkyrienskies.lod.set"
    private const val BACKEND_CURRENT_MESSAGE = "command.valkyrienskies.backend.current"
    private const val BACKEND_SET_MESSAGE = "command.valkyrienskies.backend.set"
    private const val LOD_DISABLED_MESSAGE = "command.valkyrienskies.lod.disabled"
    private const val AIR_VALUES_MESSAGE = "command.valkyrienskies.air_values"
    private const val AIR_VALUES_DENSITY_MESSAGE = "command.valkyrienskies.air_values.density"
    private const val AIR_VALUES_TEMPERATURE_MESSAGE = "command.valkyrienskies.air_values.temperature"
    private const val AIR_VALUES_PRESSURE_MESSAGE = "command.valkyrienskies.air_values.pressure"
    private const val GET_GRAVITY_MESSAGE = "command.valkyrienskies.get_gravity"
    private const val SET_SPLITTING_MESSAGE = "command.valkyrienskies.set_splitting"

    private const val GET_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.get_ship.success"
    private const val GET_SHIP_FAIL_MESSAGE = "command.valkyrienskies.get_ship.fail"
    const val VECTOR_ARG_FAIL_MESSAGE = "command.valkyrienskies.vector_arg.fail"

    private const val GET_SHIP_ONLY_USABLE_BY_ENTITIES_MESSAGE = "command.valkyrienskies.get_ship.only_usable_by_entities"
    private const val TELEPORTED_MULTIPLE_SHIPS_SUCCESS = "command.valkyrienskies.teleport.multiple_ship_success"
    private const val TELEPORT_FIRST_ARG_CAN_ONLY_INPUT_1_SHIP = "command.valkyrienskies.mc_teleport.can_only_teleport_to_one_ship"

    fun registerServerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("vs")
                // Non operator commands

                .then(literal("get-ship")
                .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.getShipCommandPerms)}
                .executes {

                    val mcCommandContext = it

                    var success = false
                    val sourceEntity: Entity? = mcCommandContext.source.entity
                    if (sourceEntity != null) {
                        val rayTrace = sourceEntity.pick(10.0, 1.0.toFloat(), false)
                        if (rayTrace is BlockHitResult) {
                            val ship = sourceEntity.level().getShipManagingPos(rayTrace.blockPos)
                            if (ship != null) {
                                it.source.sendSuccess(
                                    {
                                        translatable(GET_SHIP_SUCCESS_MESSAGE, ship.slug, ship.id)
                                    }, true
                                )
                                success = true
                            }
                        }
                        if (success) {
                            1
                        } else {
                            it.source.sendFailure(translatable(GET_SHIP_FAIL_MESSAGE))
                            0
                        }
                    } else {
                        it.source.sendFailure(translatable(GET_SHIP_ONLY_USABLE_BY_ENTITIES_MESSAGE))
                        0
                    }

                })
                // Operator commands
                .then(literal("delete")
                .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.deleteShipCommandPerms)}
                    .then(argument("ships", ShipArgument.ships())
                        .executes {
                            deleteShip(it)
                        }.then(argument("deleteBlocks", BoolArgumentType.bool())
                            .executes {
                                deleteShip(it, BoolArgumentType.getBool(it, "deleteBlocks"))
                            }
                        )
                    )
                ).then(literal("set-splitting")
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
                .then(literal("get-air").requires { it.hasPermission(VSGameConfig.SERVER.Commands.getAirValuesPerms)}
                    .executes {
                        val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                        val height = it.source.position.y
                        val dimId = it.source.level.dimensionId

                        val density = aero.getAirDensityForY(height, dimId)
                        val temperature = aero.getAirTemperatureForY(height, dimId)
                        val pressure = aero.getAirPressureForY(height, dimId)

                        it.source.sendSuccess(
                            {
                                translatable(AIR_VALUES_MESSAGE, round(height*10.0)/10, density, temperature, pressure)
                            }, true
                        )

                        1
                    }
                    .then(literal("density")
                        .executes {
                            val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                            val height = it.source.position.y
                            val dimId = it.source.level.dimensionId

                            val density = aero.getAirDensityForY(height, dimId)

                            it.source.sendSuccess(
                                {
                                    translatable(AIR_VALUES_DENSITY_MESSAGE, round(height*10.0)/10, density)
                                }, true
                            )

                            density.roundToInt()
                        }
                    )
                    .then(literal("temperature")
                        .executes {
                            val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                            val height = it.source.position.y
                            val dimId = it.source.level.dimensionId

                            val temperature = aero.getAirTemperatureForY(height, dimId)

                            it.source.sendSuccess(
                                {
                                    translatable(AIR_VALUES_TEMPERATURE_MESSAGE, round(height*10.0)/10, temperature)
                                }, true
                            )

                            temperature.roundToInt()
                        }
                    )
                    .then(literal("pressure")
                        .executes {
                            val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                            val height = it.source.position.y
                            val dimId = it.source.level.dimensionId

                            val pressure = aero.getAirPressureForY(height, dimId)

                            it.source.sendSuccess(
                                {
                                    translatable(AIR_VALUES_PRESSURE_MESSAGE, round(height*10.0)/10, pressure)
                                }, true
                            )

                            pressure.roundToInt()
                        }
                    )
                )
                .then(literal("get-gravity").requires { it.hasPermission(VSGameConfig.SERVER.Commands.getAirValuesPerms)}
                    .executes {
                        val level = it.source.level
                        val gravity = level.shipObjectWorld.aerodynamicUtils.getAtmosphereForDimension(level.dimensionId).third

                        it.source.sendSuccess(
                            {
                                translatable(GET_GRAVITY_MESSAGE, level.dimensionId.toNiceString(), gravity)
                            },
                            true
                        )

                        gravity.roundToInt()
                    }
                )
                .then(literal("backend")
                .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.changeBackendCommandPerms)}
                    .then(literal("engine")
                        .then(literal("krunch")
                            .executes {
                                VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_CLASSIC
                                (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_CLASSIC.name)

                                it.source.sendSuccess(
                                    {
                                        translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                                    }, true
                                )

                                1
                            }
                        ).then(literal("DEFAULT")
                            .executes {
                                VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_CLASSIC
                                (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_CLASSIC.name)

                                it.source.sendSuccess(
                                    {
                                        translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                                    }, true
                                )

                                1
                            }
                        ).then(literal("physx")
                            .executes {
                                VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_PHYSX
                                (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_PHYSX.name)

                                it.source.sendSuccess(
                                    {
                                        translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                                    }, true
                                )

                                1
                            }
                        ).then(literal("jolt")
                            .executes {
                                VSCoreConfig.SERVER.physics.physicsBackend = ConfigPhysicsBackendType.KRUNCH_JOLT
                                (VSConfigUpdater.forgeConfigValuesMap.get("physicsBackend") as ForgeConfigSpec.ConfigValue<String>).set(ConfigPhysicsBackendType.KRUNCH_JOLT.name)

                                it.source.sendSuccess(
                                    {
                                        translatable(BACKEND_SET_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                                    }, true
                                )

                                1
                            }
                        ).executes {
                            it.source.sendSuccess(
                                {
                                    translatable(BACKEND_CURRENT_MESSAGE, VSCoreConfig.SERVER.physics.physicsBackend.name)
                                }, true
                            )

                            1
                        }
                    )
                    .then(literal("lodDetail")
                        .then(argument("amount", IntegerArgumentType.integer(-1))
                            .executes {
                                var amount = IntegerArgumentType.getInteger(it, "amount")
                                VSCoreConfig.SERVER.physics.lodDetail = amount
                                (VSConfigUpdater.forgeConfigValuesMap.get("lodDetail") as ForgeConfigSpec.ConfigValue<Int>).set(amount)

                                val msg = if (amount == -1) {
                                    Component.translatable(LOD_DISABLED_MESSAGE)
                                } else {
                                    amount.toString()
                                }

                                it.source.sendSuccess(
                                    {
                                        translatable(LOD_SET_MESSAGE, msg)
                                    }, true
                                )

                                1
                            }
                        )
                        .executes {
                            val lod = VSCoreConfig.SERVER.physics.lodDetail
                            val msg = if (lod == -1) {
                                Component.translatable(LOD_DISABLED_MESSAGE)
                            } else {
                                lod.toString()
                            }

                            it.source.sendSuccess(
                                {
                                    translatable(LOD_CURRENT_MESSAGE, msg)
                                },
                                true
                            )

                            1
                        }
                    )
                )
                .then(literal("rename")
                .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.renameShipCommandPerms)}
                    .then(argument("ship", ShipArgument.ships())
                        .then(argument("newName", StringArgumentType.string())
                            .executes {
                                vsCore.renameShip(
                                    ShipArgument.getShip(it, "ship") as ServerShip,
                                    StringArgumentType.getString(it, "newName")
                                )
                                1
                            }
                        )
                    )
                )
                .then(
                    literal("remass")
                        .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.remassShipCommandPerms)}.then(
                            argument("ships", ShipArgument.ships()).executes {
                                val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                var successful = 0
                                r.forEach { ship ->
                                    if (BlockStateInfo.remassShip(it.source.level, ship)) {
                                        ++successful
                                    } else {
                                        it.source.sendFailure(
                                            translatable(
                                                REMASSED_SHIP_FAIL_MESSAGE, ship.slug
                                            )
                                        )
                                    }
                                }
                                it.source.sendSuccess(
                                    {
                                        translatable(
                                            REMASSED_SHIPS_SUCCESS_MESSAGE, successful
                                        )
                                    }, true
                                )
                                successful
                            })
                )
                .then(
                    literal("set-static")
                        .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.setStaticShipCommandPerms)}.then(
                        argument("ships", ShipArgument.ships()).then(
                            argument("is-static", BoolArgumentType.bool()).executes {
                                val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                val isStatic = BoolArgumentType.getBool(it, "is-static")
                                r.forEach { ship ->
                                    ship.isStatic = isStatic
                                }
                                it.source.sendSuccess(
                                    {
                                        translatable(
                                            SET_SHIP_STATIC_SUCCESS_MESSAGE, r.size, if (isStatic) "true" else "false"
                                        )
                                    }, true
                                )
                                r.size
                            })
                    )
                )
                //Scale a ship
                .then(
                    literal("scale")
                        .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.scaleShipCommandPerms)}.then(
                        argument("ship", ShipArgument.ships())
                            .then(argument("newScale", DoubleArgumentType.doubleArg(0.001))
                                .executes {
                                    vsCore.scaleShip(
                                        it.source.shipWorld as ServerShipWorld,
                                        ShipArgument.getShip(it, "ship") as ServerShip,
                                        DoubleArgumentType.getDouble(it, "newScale")
                                    )
                                    1
                                }
                            )
                    )
                )
                .then(
                    literal("teleport")
                        .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.teleportShipCommandPerms)}.then(
                        argument("ships", ShipArgument.ships()).then(
                            argument("position", Vec3Argument.vec3()).executes {
                                // If only position is present then we execute this code

                                val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                val position =
                                    Vec3Argument.getVec3(it, "position")
                                val dimensionId = it.source.level.dimensionId
                                val shipTeleportData: ShipTeleportData =
                                    vsCore.newShipTeleportData(
                                        newPos = position.toJOML(),
                                        newDimension = dimensionId
                                    )
                                r.forEach { ship ->
                                    vsCore.teleportShip(
                                        it.source.shipWorld as ServerShipWorld,
                                        ship, shipTeleportData
                                    )
                                }
                                it.source.sendSuccess(
                                    {
                                        translatable(
                                            TELEPORT_SHIP_SUCCESS_MESSAGE, r.size, shipTeleportData.getMessage()
                                        )
                                    }, true
                                )
                                r.size

                            }.then(
                                argument("euler-angles", RelativeVector3Argument.relativeVector3()).executes {
                                    // If only position is present then we execute this code

                                    val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                    val position =
                                        Vec3Argument.getVec3(it, "position")
                                    val eulerAngles =
                                        RelativeVector3Argument.getRelativeVector3(
                                            it, "euler-angles"
                                        )

                                    val source = it.source
                                    val dimensionId = it.source.level.dimensionId
                                    val shipTeleportData: ShipTeleportData =
                                        vsCore.newShipTeleportData(
                                            newPos = position.toJOML(),
                                            newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                            ),
                                            newDimension = dimensionId
                                        )
                                    r.forEach { ship ->
                                        vsCore.teleportShip(
                                            it.source.shipWorld as ServerShipWorld,
                                            ship, shipTeleportData
                                        )
                                    }
                                    it.source.sendSuccess(
                                        {
                                            translatable(
                                                TELEPORT_SHIP_SUCCESS_MESSAGE, r.size, shipTeleportData.getMessage()
                                            )
                                        }, true
                                    )
                                    r.size

                                }.then(
                                    argument("velocity", RelativeVector3Argument.relativeVector3()).executes {
                                        // If only position is present then we execute this code

                                        val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                        val position =
                                            Vec3Argument.getVec3(
                                                it, "position"
                                            )
                                        val eulerAngles =
                                            RelativeVector3Argument.getRelativeVector3(
                                                it, "euler-angles"
                                            )
                                        val velocity = RelativeVector3Argument.getRelativeVector3(
                                            it, "velocity"
                                        )

                                        val source = it.source
                                        val dimensionId = it.source.level.dimensionId
                                        val shipTeleportData: ShipTeleportData =
                                            vsCore.newShipTeleportData(
                                                newPos = position.toJOML(),
                                                newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                    source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                ),
                                                newVel = velocity.toVector3d(0.0, 0.0, 0.0),
                                                newDimension = dimensionId
                                            )
                                        r.forEach { ship ->
                                            vsCore.teleportShip(
                                                it.source.shipWorld as ServerShipWorld,
                                                ship, shipTeleportData
                                            )
                                        }
                                        it.source.sendSuccess(
                                            {
                                                translatable(
                                                    TELEPORT_SHIP_SUCCESS_MESSAGE, r.size, shipTeleportData.getMessage()
                                                )
                                            }, true
                                        )
                                        r.size

                                    }.then(
                                        argument(
                                            "angular-velocity", RelativeVector3Argument.relativeVector3()
                                        ).executes {
                                            // If only position is present then we execute this code

                                            val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                                            val position =
                                                Vec3Argument.getVec3(
                                                    it, "position"
                                                )
                                            val eulerAngles =
                                                RelativeVector3Argument.getRelativeVector3(
                                                    it, "euler-angles"
                                                )
                                            val velocity = RelativeVector3Argument.getRelativeVector3(
                                                it, "velocity"
                                            )
                                            val angularVelocity = RelativeVector3Argument.getRelativeVector3(
                                                it, "angular-velocity"
                                            )

                                            val source = it.source
                                            val dimensionId = it.source.level.dimensionId
                                            val shipTeleportData: ShipTeleportData =
                                                vsCore.newShipTeleportData(
                                                    newPos = position.toJOML(),
                                                    newRot = eulerAngles.toEulerRotationFromMCEntity(
                                                        source.rotation.x.toDouble(), source.rotation.y.toDouble(),
                                                    ),
                                                    newVel = velocity.toVector3d(0.0, 0.0, 0.0),
                                                    newOmega = angularVelocity.toVector3d(0.0, 0.0, 0.0),
                                                    newDimension = dimensionId
                                                )
                                            r.forEach { ship ->
                                                vsCore.teleportShip(
                                                    it.source.shipWorld as ServerShipWorld,
                                                    ship, shipTeleportData
                                                )
                                            }
                                            it.source.sendSuccess(
                                                {
                                                    translatable(
                                                        TELEPORT_SHIP_SUCCESS_MESSAGE, r.size,
                                                        shipTeleportData.getMessage()
                                                    )
                                                },
                                                true
                                            )
                                            r.size

                                        }
                                    )
                                )
                            )
                        )
                    )
                )


        )

        // TODO: fix this? It horrifically mangles the vanilla tp command at the moment
        /*dispatcher.root.children.firstOrNull { it.name == "teleport" }?.apply {
            addChild(
                argument("ships", ShipArgument.selectorOnly()).executes {
                    val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                    val serverShip = serverShips.singleOrNull() ?: throw CommandRuntimeException(
                        translatable(TELEPORT_FIRST_ARG_CAN_ONLY_INPUT_1_SHIP)
                    )
                    val source = it.source
                    val shipPos = serverShip.transform.positionInWorld

                    source.entity?.let { entity -> entity.teleportTo(shipPos.x, shipPos.y, shipPos.z); 1 } ?: 0
                }.then(
                    argument("entity", EntityArgument.entity()).executes {
                        val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        val entity = EntityArgument.getEntity(it, "entity")

                        serverShips.forEach { serverShip ->
                            vsCore.teleportShip(
                                it.source.shipWorld as ServerShipWorld, serverShip,
                                vsCore.newShipTeleportData(newPos = Vector3d(entity.x, entity.y, entity.z))
                            )
                        }
                        it.source.sendVSMessage(
                            translatable(TELEPORTED_MULTIPLE_SHIPS_SUCCESS, serverShips.size)
                        )
                        serverShips.size
                    }
                ).then(
                    argument("pos", BlockPosArgument.blockPos()).executes {
                        val serverShips = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        val pos = BlockPosArgument.getSpawnablePos(it, "pos")

                        serverShips.forEach { serverShip ->
                            vsCore.teleportShip(
                                it.source.shipWorld as ServerShipWorld, serverShip,
                                vsCore.newShipTeleportData(newPos = pos.toJOMLD())
                            )
                        }
                        it.source.sendVSMessage(
                            translatable(TELEPORTED_MULTIPLE_SHIPS_SUCCESS, serverShips.size)
                        )
                        serverShips.size
                    }
                ).build()
            )

            getChild("targets").addChild(
                argument("ship", ShipArgument.selectorOnly()).executes {
                    val ship = ShipArgument.getShip(it, "ship")
                    val entities = EntityArgument.getEntities(it, "targets")
                    val shipPos = ship.transform.positionInWorld

                    entities.forEach { entity -> entity.teleportTo(shipPos.x, shipPos.y, shipPos.z) }

                    entities.size
                }.build()
            )
        }*/
    }

    fun deleteShip(context: CommandContext<CommandSourceStack>, deleteBlocks: Boolean = false): Int {
        val r = ShipArgument.getShips(context, "ships").toList() as List<ServerShip>

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

    fun registerClientCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // TODO implement client commands
    }
}

/*val CommandSourceStack.shipWorld: ShipWorld
    get() = (this.level.shipObjectWorld)*/

private fun ShipTeleportData.getMessage(): String {
    // "(${this.newDimension}) ${this.newPos}, rotation ${this.newRot}, velocity ${this.newVel}"
    return translatable(
        "command.valkyrienskies.teleport.teleport_data",
        this.newDimension?.toNiceString() ?: "",
        this.newPos.toNiceString(),
        this.newRot.toNiceString(),
        this.newVel.toNiceString()
    ).string
}

private fun Vector3dc.toNiceString(): String {
    return (this as Vector3d).toString(NumberFormat.getInstance())
}

private fun Quaterniondc.toNiceString(): String {
    return (this as Quaterniond).toString(NumberFormat.getInstance())
}

private fun DimensionId.toNiceString(): String {
    val split = this.split(":")
    return split[2] + split[3]
}

val SharedSuggestionProvider.shipWorld: ShipWorld
    get() {
        return (
            if (this is CommandSourceStack) {
                return this.level.shipObjectWorld
            } else if (this is ClientSuggestionProviderAccessor) {
                checkNotNull(this.minecraft.level)
                return this.minecraft.level.shipObjectWorld
            } else {
                // Shouldn't happen
                throw CommandRuntimeException(Component.literal("Command source wasn't CommandSourceStack or ClientSuggestionProvider? Please report this as a bug"))
            }
            )
    }

