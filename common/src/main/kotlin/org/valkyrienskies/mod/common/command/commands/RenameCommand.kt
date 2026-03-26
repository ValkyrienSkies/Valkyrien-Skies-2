package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.vsCore

object RenameCommand {
    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("rename")
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
    }
}
