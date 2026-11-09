package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.RotationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.vsCore
import kotlin.math.atan2
import kotlin.math.sqrt

object VanillaTeleportCommand {
    private const val PERMISSION_LEVEL = 2
    private const val ERROR_ONE_SHIP_DESTINATION = "command.valkyrienskies.mc_teleport.can_only_teleport_to_one_ship"
    private const val ENTITY_TO_SHIP_SUCCESS = "command.valkyrienskies.mc_teleport.entity_to_ship"
    private const val SHIP_TO_ENTITY_SUCCESS = "command.valkyrienskies.mc_teleport.ship_to_entity"
    private const val SHIP_TO_SHIP_SUCCESS = "command.valkyrienskies.mc_teleport.ship_to_ship"
    private const val SHIP_TO_POS_SUCCESS = "command.valkyrienskies.mc_teleport.ship_to_pos"

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(build("teleport"))
    }

    private fun build(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        literal(name)
            .requires { it.hasPermission(PERMISSION_LEVEL) }
            .then(
                argument("shipTargets", ShipArgument.selectorOnly())
                    .executes { teleportSourceToShip(it) }
                    .then(shipPositionArgument())
                    .then(
                        argument("entityDestination", EntityArgument.entity())
                            .executes { teleportShipsToEntity(it) }
                    )
                    .then(
                        argument("shipDestination", ShipArgument.selectorOnly())
                            .executes { teleportShipsToShip(it) }
                    )
            )
            .then(
                argument("targets", EntityArgument.entities())
                    .then(
                        argument("shipDestination", ShipArgument.selectorOnly())
                            .executes { teleportEntitiesToShip(it) }
                    )
                    .then(entityPositionArgument())
            )

    private fun shipPositionArgument() =
        argument("position", Vec3Argument.vec3())
            .executes { teleportShipsToPosition(it) }
            .then(
                argument("rotation", RotationArgument.rotation())
                    .executes { teleportShipsToPositionWithRotation(it) }
            )
            .then(
                literal("facing")
                    .then(
                        argument("facingLocation", Vec3Argument.vec3())
                            .executes { teleportShipsToPositionFacingLocation(it) }
                    )
                    .then(
                        literal("entity")
                            .then(
                                argument("facingEntity", EntityArgument.entity())
                                    .executes { teleportShipsToPositionFacingEntity(it) }
                                    .then(
                                        argument("facingAnchor", EntityAnchorArgument.anchor())
                                            .executes { teleportShipsToPositionFacingEntity(it) }
                                    )
                            )
                    )
                    .then(
                        literal("ship")
                            .then(
                                argument("facingShip", ShipArgument.selectorOnly())
                                    .executes { teleportShipsToPositionFacingShip(it) }
                            )
                    )
            )

    private fun entityPositionArgument(): RequiredArgumentBuilder<CommandSourceStack, *> =
        argument("position", Vec3Argument.vec3())
            .then(
                literal("facing")
                    .then(
                        literal("ship")
                            .then(
                                argument("facingShip", ShipArgument.selectorOnly())
                                    .executes { teleportEntitiesToPositionFacingShip(it) }
                            )
                    )
            )

    private fun teleportSourceToShip(context: CommandContext<CommandSourceStack>): Int =
        teleportEntitiesToShip(context, listOf(context.source.entityOrException), "shipTargets")

    private fun teleportEntitiesToShip(context: CommandContext<CommandSourceStack>): Int =
        teleportEntitiesToShip(context, EntityArgument.getEntities(context, "targets"), "shipDestination")

    private fun teleportEntitiesToShip(
        context: CommandContext<CommandSourceStack>,
        targets: Collection<Entity>,
        destinationArgument: String
    ): Int {
        val source = context.source
        val destination = getSingleShip(context, destinationArgument)
        val level = getShipLevel(source, destination)
        val pos = destination.transform.positionInWorld.toMinecraft()

        targets.forEach { entity ->
            entity.teleportTo(level, pos.x, pos.y, pos.z, emptySet<RelativeMovement>(), entity.yRot, entity.xRot)
        }

        source.sendSuccess(
            { Component.translatable(ENTITY_TO_SHIP_SUCCESS, targets.size, destination.slug ?: destination.id.toString()) },
            true
        )
        return targets.size
    }

    private fun teleportEntitiesToPositionFacingShip(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = EntityArgument.getEntities(context, "targets")
        val position = Vec3Argument.getVec3(context, "position")
        val facingShip = getSingleShip(context, "facingShip")
        val facingPosition = facingShip.transform.positionInWorld.toMinecraft()

        targets.forEach { entity ->
            performEntityTeleport(source, entity, source.level, position, facingPosition)
        }

        source.sendSuccess(
            {
                if (targets.size == 1) {
                    Component.translatable(
                        "commands.teleport.success.location.single",
                        targets.single().displayName,
                        formatDouble(position.x),
                        formatDouble(position.y),
                        formatDouble(position.z)
                    )
                } else {
                    Component.translatable(
                        "commands.teleport.success.location.multiple",
                        targets.size,
                        formatDouble(position.x),
                        formatDouble(position.y),
                        formatDouble(position.z)
                    )
                }
            },
            true
        )
        return targets.size
    }

    private fun performEntityTeleport(
        source: CommandSourceStack,
        entity: Entity,
        level: ServerLevel,
        position: Vec3,
        facingPosition: Vec3
    ) {
        if (!Level.isInSpawnableBounds(net.minecraft.core.BlockPos.containing(position))) {
            return
        }

        val teleported = entity.teleportTo(
            level,
            position.x,
            position.y,
            position.z,
            setOf(RelativeMovement.X_ROT, RelativeMovement.Y_ROT),
            entity.yRot,
            entity.xRot
        )
        if (!teleported) {
            return
        }

        if (entity is ServerPlayer) {
            entity.lookAt(source.anchor, facingPosition)
        } else {
            entity.lookAt(source.anchor, facingPosition)
        }

        if (entity !is LivingEntity || !entity.isFallFlying) {
            entity.deltaMovement = entity.deltaMovement.multiply(1.0, 0.0, 1.0)
            entity.setOnGround(true)
        }

        if (entity is PathfinderMob) {
            entity.navigation.stop()
        }
    }


    private fun teleportShipsToPosition(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = getShipTargets(context)
        val position = Vec3Argument.getVec3(context, "position")

        teleportShips(source, targets, source.level, position, null)

        source.sendSuccess(
            {
                Component.translatable(
                    SHIP_TO_POS_SUCCESS,
                    targets.size,
                    position.x,
                    position.y,
                    position.z
                )
            },
            true
        )
        return targets.size
    }

    private fun teleportShipsToPositionWithRotation(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = getShipTargets(context)
        val position = Vec3Argument.getVec3(context, "position")
        val rotation = RotationArgument.getRotation(context, "rotation").getRotation(source)

        teleportShips(source, targets, source.level, position, rotationToShipRotation(rotation.x.toDouble(), rotation.y.toDouble()))

        source.sendSuccess(
            { Component.translatable(SHIP_TO_POS_SUCCESS, targets.size, position.x, position.y, position.z) },
            true
        )
        return targets.size
    }

    private fun teleportShipsToPositionFacingLocation(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = getShipTargets(context)
        val position = Vec3Argument.getVec3(context, "position")
        val facingPosition = Vec3Argument.getVec3(context, "facingLocation")

        teleportShips(source, targets, source.level, position, rotationFacing(position, facingPosition))

        source.sendSuccess(
            { Component.translatable(SHIP_TO_POS_SUCCESS, targets.size, position.x, position.y, position.z) },
            true
        )
        return targets.size
    }

    private fun teleportShipsToPositionFacingEntity(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = getShipTargets(context)
        val position = Vec3Argument.getVec3(context, "position")
        val facingEntity = EntityArgument.getEntity(context, "facingEntity")
        val anchor = try {
            EntityAnchorArgument.getAnchor(context, "facingAnchor")
        } catch (_: IllegalArgumentException) {
            EntityAnchorArgument.Anchor.FEET
        }
        val facingPosition = anchor.apply(facingEntity)

        teleportShips(source, targets, source.level, position, rotationFacing(position, facingPosition))

        source.sendSuccess(
            { Component.translatable(SHIP_TO_POS_SUCCESS, targets.size, position.x, position.y, position.z) },
            true
        )
        return targets.size
    }

    private fun teleportShipsToPositionFacingShip(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = getShipTargets(context)
        val position = Vec3Argument.getVec3(context, "position")
        val facingShip = getSingleShip(context, "facingShip")
        val facingPosition = facingShip.transform.positionInWorld.toMinecraft()

        teleportShips(source, targets, source.level, position, rotationFacing(position, facingPosition))

        source.sendSuccess(
            { Component.translatable(SHIP_TO_POS_SUCCESS, targets.size, position.x, position.y, position.z) },
            true
        )
        return targets.size
    }

    private fun teleportShipsToEntity(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = getShipTargets(context)
        val destination = EntityArgument.getEntity(context, "entityDestination")
        val level = destination.level() as ServerLevel
        val position = level.toWorldCoordinates(destination.position())
        val rotation = rotationToShipYaw(destination.yRot.toDouble())

        teleportShips(source, targets, level, position, rotation)

        source.sendSuccess(
            { Component.translatable(SHIP_TO_ENTITY_SUCCESS, targets.size, destination.displayName) },
            true
        )
        return targets.size
    }

    private fun teleportShipsToShip(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val targets = getShipTargets(context)
        val destination = getSingleShip(context, "shipDestination")
        val level = getShipLevel(source, destination)
        val position = destination.transform.positionInWorld.toMinecraft()
        val rotation = if (destination is LoadedShip) Quaterniond(destination.transform.shipToWorldRotation) else null

        teleportShips(source, targets, level, position, rotation)

        source.sendSuccess(
            { Component.translatable(SHIP_TO_SHIP_SUCCESS, targets.size, destination.slug ?: destination.id.toString()) },
            true
        )
        return targets.size
    }

    private fun teleportShips(
        source: CommandSourceStack,
        ships: Collection<ServerShip>,
        level: ServerLevel,
        position: Vec3,
        rotation: Quaterniond?
    ) {
        val teleportData = if (rotation == null) {
            vsCore.newShipTeleportData(
                newPos = position.toJOML(),
                newDimension = level.dimensionId
            )
        } else {
            vsCore.newShipTeleportData(
                newPos = position.toJOML(),
                newRot = rotation,
                newDimension = level.dimensionId
            )
        }
        ships.forEach { ship ->
            vsCore.teleportShip(source.server.shipObjectWorld as ServerShipWorld, ship, teleportData)
        }
    }

    private fun getShipTargets(context: CommandContext<CommandSourceStack>): List<ServerShip> =
        ShipArgument.getShips(context, "shipTargets").map { it as ServerShip }.toList()

    private fun getSingleShip(context: CommandContext<CommandSourceStack>, argumentName: String): Ship {
        val ships = ShipArgument.getShips(context, argumentName).toList()
        if (ships.size != 1) {
            throw com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Component.translatable(ERROR_ONE_SHIP_DESTINATION)
            ).create()
        }
        return ships.single()
    }

    private fun getShipLevel(source: CommandSourceStack, ship: Ship): ServerLevel =
        source.server.getLevelFromDimensionId(ship.chunkClaimDimension) ?: source.level

    private fun rotationFacing(from: Vec3, to: Vec3): Quaterniond {
        val deltaX = to.x - from.x
        val deltaY = to.y - from.y
        val deltaZ = to.z - from.z
        val horizontal = sqrt(deltaX * deltaX + deltaZ * deltaZ)
        val yaw = Math.toDegrees(atan2(deltaZ, deltaX)) - 90.0
        val pitch = -Math.toDegrees(atan2(deltaY, horizontal))
        return rotationToShipRotation(pitch, yaw)
    }

    private fun rotationToShipRotation(pitch: Double, yaw: Double): Quaterniond =
        Quaterniond().rotateZYX(0.0, Math.toRadians(-yaw), Math.toRadians(pitch))

    private fun rotationToShipYaw(yaw: Double): Quaterniond =
        Quaterniond().rotateY(Math.toRadians(-yaw))

    private fun formatDouble(value: Double): String = String.format(java.util.Locale.ROOT, "%f", value)
}
