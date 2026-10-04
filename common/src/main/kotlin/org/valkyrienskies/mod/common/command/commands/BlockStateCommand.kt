package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
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

                send("State found at (${hit.blockPos.toShortString()})")
                send("Serialized: ${state.toFullString()}")
                send("Serialized fluid: ${state.fluidState?.toFullString() ?: "none"}")

                send("Block type: ${state.vsType}")
                send("Composition: ${getComposition(state)}")
                send("VS State:")
                send("Solid: ${vsState.solidState?.toString() ?: "none"}")
                send("Liquid: ${vsState.liquidState?.toString() ?: "none"}")
                send("Displacement: ${vsState.displacementState?.toString() ?: "none"}")

                send("Calculated mass: ${state.mass}")
                send("Calculated solid state mass: ${vsState.solidState?.mass ?: "none"}")
                send("Calculated liquid state mass: ${vsState.liquidState?.actualMass ?: "none"}")
                1
            }
        )
    }

    private fun BlockPos.readable(): String =
        "($x, $y, $z)"
}
