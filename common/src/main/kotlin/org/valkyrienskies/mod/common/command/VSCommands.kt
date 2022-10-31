package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import org.valkyrienskies.core.commands.VSCommand
import org.valkyrienskies.core.commands.VSCommandSource

object VSCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        VSCommand.register(dispatcher as CommandDispatcher<VSCommandSource>)
    }
}
