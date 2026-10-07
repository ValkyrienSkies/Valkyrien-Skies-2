package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import org.valkyrienskies.core.api.physics.blockstates.DisplacementState
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.mod.common.blockstate.Composition
import org.valkyrienskies.mod.common.blockstate.actualMass
import org.valkyrienskies.mod.common.blockstate.getComposition
import org.valkyrienskies.mod.common.blockstate.getVsiBlockState
import org.valkyrienskies.mod.common.blockstate.mass
import org.valkyrienskies.mod.common.blockstate.toFullString
import org.valkyrienskies.mod.common.blockstate.vsType
import org.valkyrienskies.mod.common.config.VSGameConfig

object BlockStateCommand {

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("blockstate").requires { it.hasPermission(VSGameConfig.SERVER.Commands.blockstateCommandPerms) }
            .executes {
                fun send(message: String) {
                    it.source.sendSuccess({ Component.literal(message) }, false)
                }
                val hit = it.source.entityOrException.pick(20.0, 0f, true)
                if (hit == null || hit !is BlockHitResult) {
                    send("No block found!")
                    return@executes 1
                }

                val state = it.source.level.getBlockState(hit.blockPos)
                val vsState = it.source.level.getVsiBlockState(hit.blockPos)
                val composition = getComposition(state)

                send("----------------------------------------------------")
                send("State found at (${hit.blockPos.toShortString()})")
                send("Serialized: ${state.toFullString()}")
                send("Serialized fluid: ${state.fluidState?.toFullString() ?: "none"}")

                send("Block type: ${state.vsType}")
                send("Composition: $composition")
                send("Calculated mass: ${String.format("%.3f", state.mass)}")

                send("VS State:")
                when (composition) {
                    Composition.SOLID, Composition.EMPTY -> {
                        send(vsState.solidState?.betterStringThatMakesMoreSense() ?: "  Solid state: none")
                        send(vsState.liquidState?.betterStringThatMakesMoreSenseButAlsoMediumState() ?: "  Medium state: none")
                        send(vsState.displacementState?.reallyGoodString() ?: "  Displacement state: none")
                    }
                    Composition.MIXED -> {
                        send(vsState.solidState?.betterStringThatMakesMoreSense() ?: "  Solid state: none")
                        send(vsState.liquidState?.betterStringThatMakesMoreSense() ?: "  Medium state: none")
                        send(vsState.displacementState?.reallyGoodString() ?: "Displacement state: none")
                    }
                    Composition.LIQUID -> {
                        send(vsState.liquidState?.betterStringThatMakesMoreSense() ?: "Liquid state: none")
                    }
                    Composition.AIR -> {
                        send("State is air!")
                    }
                }
                1
            }
        )
    }

    private fun SolidState.betterStringThatMakesMoreSense() =
        "  Solid state:\n" +
            "  | Mass: $mass\n" +
            "  | Friction: $friction\n" +
            "  | Elasticity: $elasticity"

    private fun LiquidState.betterStringThatMakesMoreSense() =
        "  Liquid state:\n" +
            "  | Density: $density\n" +
            "  | Drag: $dragCoefficient\n" +
            "  | Velocity: (${velocity.x()}, ${velocity.y()}, ${velocity.z()})" +
            "  | Shape: ${shape.boundingBox}" +
            "  | Calculated mass (based off shape and density): ${String.format("%.3f", actualMass)}"


    private fun LiquidState.betterStringThatMakesMoreSenseButAlsoMediumState() =
        "  Medium state:\n" +
            "  | Drag: $dragCoefficient\n" +
            "  | Shape: ${shape.boundingBox}"

    private fun DisplacementState.reallyGoodString() =
        "  Displacement state:\n" +
            "  | Shape: ${shape.boundingBox}"
}
