package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.mod.common.command.toNiceString
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.math.roundToInt

object GetGravityCommand {
    private const val GET_GRAVITY_MESSAGE = "command.valkyrienskies.get_gravity"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("get-gravity").requires { it.hasPermission(VSGameConfig.SERVER.Commands.getAirValuesPerms)}
            .executes {
                val level = it.source.level
                val gravity = level.shipObjectWorld.aerodynamicUtils.getAtmosphereForDimension(level.dimensionId).third

                it.source.sendSuccess(
                    {
                        translatable(GET_GRAVITY_MESSAGE, level.dimensionId.toNiceString(), gravity)
                    },
                    true
                )

                gravity.roundToInt()
            }
        )
    }
}
