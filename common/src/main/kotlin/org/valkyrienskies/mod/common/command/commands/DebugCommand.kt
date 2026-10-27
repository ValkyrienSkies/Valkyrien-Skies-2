package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import org.valkyrienskies.mod.common.debug.DebugRendererMode
import org.valkyrienskies.mod.common.debug.ShipDebugService

object DebugCommand {
    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        val renderer = literal("renderer").executes { setMode(it.source, null) }
        for (mode in DebugRendererMode.values()) {
            renderer.then(literal(mode.commandName).executes { setMode(it.source, mode) })
        }
        vs.then(literal("debug").requires { it.hasPermission(2) }.then(renderer))
    }

    private fun setMode(source: CommandSourceStack, value: DebugRendererMode?): Int {
        val mode = ShipDebugService.setMode(source.playerOrException, value)
        source.sendSuccess({ Component.translatable(
            "command.valkyrienskies.debug.renderer.${mode.commandName}",
        ) }, false)
        return 1
    }
}
