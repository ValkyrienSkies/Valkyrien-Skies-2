package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.VSGameConfig
import kotlin.collections.forEach

object RemassCommand {
    private const val REMASSED_SHIPS_SUCCESS_MESSAGE = "command.valkyrienskies.remass.success"
    private const val REMASSED_SHIP_FAIL_MESSAGE = "command.valkyrienskies.remass.fail"
    private const val REMASSED_ONE_SHIP_MESSAGE = "command.valkyrienskies.remass.success_one"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(
            literal("remass")
                .requires{ it.hasPermission(VSGameConfig.SERVER.Commands.remassShipCommandPerms)}.then(
                    argument("ships", ShipArgument.ships()).executes {
                        val r = ShipArgument.getShips(it, "ships").toList() as List<ServerShip>
                        var successful = 0
                        r.forEach { ship ->
                            if (BlockStateInfo.remassShip(it.source.level, ship)) {
                                ++successful
                            } else {
                                it.source.sendFailure(
                                    translatable(
                                        REMASSED_SHIP_FAIL_MESSAGE, ship.slug
                                    )
                                )
                            }
                        }
                        it.source.sendSuccess(
                            {
                                translatable(
                                    REMASSED_SHIPS_SUCCESS_MESSAGE, successful
                                )
                            }, true
                        )
                        successful
                    })
        )
    }
}
