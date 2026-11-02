package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.util.toMinecraft

object VanillaExecuteCommand {
    private const val PERMISSION_LEVEL = 2

    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val executeNode = dispatcher.root.getChild("execute") ?: return

        dispatcher.register(
            literal("execute")
                .requires { it.hasPermission(PERMISSION_LEVEL) }
                .then(
                    literal("at")
                        .then(
                            argument("shipTargets", ShipArgument.selectorOnly())
                                .fork(executeNode) { context -> executeAtShips(context) }
                        )
                )
        )
    }

    private fun executeAtShips(context: CommandContext<CommandSourceStack>): Collection<CommandSourceStack> {
        val source = context.source

        return ShipArgument.getShips(context, "shipTargets")
            .map { ship -> source.atShip(ship) }
    }

    private fun CommandSourceStack.atShip(ship: Ship): CommandSourceStack {
        val level = server.getLevelFromDimensionId(ship.chunkClaimDimension) ?: level
        return withLevel(level)
            .withPosition(ship.transform.positionInWorld.toMinecraft())
    }
}
