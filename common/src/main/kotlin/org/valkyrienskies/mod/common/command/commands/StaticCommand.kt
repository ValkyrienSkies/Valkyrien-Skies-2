package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig
import kotlin.collections.forEach

object StaticCommand {
    private const val SET_SHIP_STATIC_SUCCESS_MESSAGE = "command.valkyrienskies.set_static.success"
    private const val SET_ONE_SHIP_STATIC_SUCCESS_MESSAGE = "command.valkyrienskies.set_static.success_one"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(
            literal("set-static")
                .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.setStaticShipCommandPerms)}.then(
                    argument("ships", ShipArgument.ships()).then(
                        argument("is-static", BoolArgumentType.bool()).executes {
                            val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                            val isStatic = BoolArgumentType.getBool(it, "is-static")
                            r.forEach { ship ->
                                ship.isStatic = isStatic
                            }
                            it.source.sendSuccess(
                                {
                                    translatable(
                                        SET_SHIP_STATIC_SUCCESS_MESSAGE, r.size, if (isStatic) "true" else "false"
                                    )
                                }, true
                            )
                            r.size
                        })
                )
        )
    }
}
