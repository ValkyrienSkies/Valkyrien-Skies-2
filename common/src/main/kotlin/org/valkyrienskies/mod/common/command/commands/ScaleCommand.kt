package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.command.shipWorld
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.vsCore

object ScaleCommand {
    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(
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
    }
}
