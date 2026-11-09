package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.api.toJOML
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.command.arguments.RelativeValue
import org.valkyrienskies.mod.common.command.arguments.RelativeVector3
import org.valkyrienskies.mod.common.command.arguments.RelativeVector3Argument
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig

object ApplyCommand {
    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("apply")
            .requires { it.hasPermission(VSGameConfig.SERVER.Commands.applyCommandPerms) }
            .then(
                literal("force")
                    .then(
                        sharedArguments {

                            executes { ctx ->
                                return@executes applyForce(ctx, Mode.WORLD)
                            }

                            then(
                                argument("mode", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        Mode.entries.forEach {
                                            builder.suggest(it.name.lowercase())
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx ->
                                        val modeName = StringArgumentType.getString(ctx, "mode")
                                        var mode: Mode?
                                        try {
                                            mode = Mode.valueOf(
                                                modeName.uppercase()
                                            )
                                        } catch (_: IllegalArgumentException) {
                                            ctx.source.sendFailure(Component.translatable("argument.valkyrienskies.ship.unknown_option", modeName))
                                            return@executes 0
                                        }

                                        return@executes applyForce(ctx, mode)
                                    }
                                    .then(argument("pos", RelativeVector3Argument.relativeVector3()).executes { ctx ->
                                        val modeName = StringArgumentType.getString(ctx, "mode")
                                        var mode: Mode?
                                        try {
                                            mode = Mode.valueOf(
                                                modeName.uppercase()
                                            )
                                        } catch (_: IllegalArgumentException) {
                                            ctx.source.sendFailure(Component.translatable("argument.valkyrienskies.ship.unknown_option", modeName))
                                            return@executes 0
                                        }

                                        val pos = RelativeVector3Argument.getRelativeVector3(ctx, "pos").toVector3d(0.0, 0.0, 0.0)

                                        return@executes applyForce(ctx, mode, pos)
                                    })
                            )
                        }
                    )
            )
            .then(
                literal("torque")
                    .then(
                        sharedArguments {
                            executes { ctx ->
                                return@executes applyTorque(ctx, Mode.WORLD)
                            }

                            then(
                                argument("mode", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        Mode.entries.forEach {
                                            builder.suggest(it.name.lowercase())
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { ctx ->
                                        val modeName = StringArgumentType.getString(ctx, "mode")
                                        var mode: Mode?
                                        try {
                                            mode = Mode.valueOf(
                                                modeName.uppercase()
                                            )
                                        } catch (_: IllegalArgumentException) {
                                            ctx.source.sendFailure(
                                                Component.translatable(
                                                    "argument.valkyrienskies.ship.unknown_option", modeName
                                                )
                                            )
                                            return@executes 0
                                        }

                                        return@executes applyTorque(ctx, mode)
                                    }
                            )
                        }
                    )
            )
        )

    }

    private fun applyTorque(ctx: CommandContext<CommandSourceStack>, mode: Mode): Int {
        val ships = ShipArgument.getShips(ctx, "ships")
        if (ships.isEmpty()) {
            ctx.source.sendFailure(
                Component.translatable("command.valkyrienskies.get_ship.fail")
            )
            return 0
        }

        val vec = RelativeVector3Argument.getRelativeVector3(ctx, "vec")

        ships.forEach {
            applyTorque(it as ServerShip, vec, mode)
        }

        return 1
    }

    private fun applyTorque(ship: ServerShip, force: RelativeVector3, mode: Mode) {
        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(ship.chunkClaimDimension)
        val force = force.toVector3dMul(ship.inertiaData.mass,ship.inertiaData.mass,ship.inertiaData.mass)
        when (mode) {
            Mode.WORLD -> gtpa.applyWorldTorque(ship.id, force)
            Mode.BODY -> gtpa.applyBodyTorque(ship.id, force)
            Mode.MODEL -> gtpa.applyModelTorque(ship.id, force)
        }
    }

    private fun applyForce(ctx: CommandContext<CommandSourceStack>, mode: Mode, position: Vector3d? = null): Int {
        val ships = ShipArgument.getShips(ctx, "ships")
        if (ships.isEmpty()) {
            ctx.source.sendFailure(
                Component.translatable("command.valkyrienskies.get_ship.fail")
            )
            return 0
        }

        val vec = RelativeVector3Argument.getRelativeVector3(ctx, "vec")

        ships.forEach {
            applyForce(it as ServerShip, vec, mode, position)
        }

        return 1
    }

    private fun applyForce(ship: ServerShip, force: RelativeVector3, mode: Mode, position: Vector3d?) {
        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(ship.chunkClaimDimension)
        val force = force.toVector3dMul(ship.inertiaData.mass,ship.inertiaData.mass,ship.inertiaData.mass)
        when (mode) {
            Mode.WORLD -> gtpa.applyWorldForce(ship.id, force, position)
            Mode.BODY -> gtpa.applyBodyForce(ship.id, force, position ?: Vector3d())
            Mode.MODEL -> gtpa.applyModelForce(ship.id, force, position)
        }
    }

    private fun sharedArguments(
        build: RequiredArgumentBuilder<CommandSourceStack, RelativeVector3>.() -> Unit
    ): ArgumentBuilder<CommandSourceStack, *> {
        return argument("ships", ShipArgument.ships()).then(
            argument("vec", RelativeVector3Argument.relativeVector3()).apply(build)
        )
    }

    private enum class Mode {
        WORLD,
        BODY,
        MODEL
    }

    // These are so that we can use ~ syntax but with * instead of +
    private fun RelativeVector3.toVector3dMul(sourceX: Double, sourceY: Double, sourceZ: Double): Vector3d = Vector3d(
        this.x.getRelativeValueMul(sourceX), this.y.getRelativeValueMul(sourceY), this.z.getRelativeValueMul(sourceZ)
    )
    private fun RelativeValue.getRelativeValueMul(sourceAngleDegrees: Double): Double {
        return if (this.isRelative) sourceAngleDegrees * angleDegrees else angleDegrees
    }
}
