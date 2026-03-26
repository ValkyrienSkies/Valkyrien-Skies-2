package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.internal.ShipTeleportData
import org.valkyrienskies.mod.common.command.arguments.RelativeVector3Argument
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.command.getMessage
import org.valkyrienskies.mod.common.command.shipWorld
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.vsCore
import kotlin.collections.forEach

object TeleportCommand {
    private const val TELEPORT_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.teleport.success"
    private const val TELEPORT_ONE_SHIP_SUCCESS_MESSAGE = "command.valkyrienskies.teleport.success_one"
    private const val TELEPORTED_MULTIPLE_SHIPS_SUCCESS = "command.valkyrienskies.teleport.multiple_ship_success"
    private const val TELEPORT_FIRST_ARG_CAN_ONLY_INPUT_1_SHIP = "command.valkyrienskies.mc_teleport.can_only_teleport_to_one_ship"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(
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
    }
}
