package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.math.round
import kotlin.math.roundToInt

object GetAirCommand {
    private const val AIR_VALUES_MESSAGE = "command.valkyrienskies.air_values"
    private const val AIR_VALUES_DENSITY_MESSAGE = "command.valkyrienskies.air_values.density"
    private const val AIR_VALUES_TEMPERATURE_MESSAGE = "command.valkyrienskies.air_values.temperature"
    private const val AIR_VALUES_PRESSURE_MESSAGE = "command.valkyrienskies.air_values.pressure"

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("get-air").requires { it.hasPermission(VSGameConfig.SERVER.Commands.getAirValuesPerms)}
            .executes {
                val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                val height = it.source.position.y
                val dimId = it.source.level.dimensionId

                val density = aero.getAirDensityForY(height, dimId)
                val temperature = aero.getAirTemperatureForY(height, dimId)
                val pressure = aero.getAirPressureForY(height, dimId)

                it.source.sendSuccess(
                    {
                        translatable(AIR_VALUES_MESSAGE, round(height*10.0)/10, density, temperature, pressure)
                    }, true
                )

                1
            }
            .then(literal("density")
                .executes {
                    val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                    val height = it.source.position.y
                    val dimId = it.source.level.dimensionId

                    val density = aero.getAirDensityForY(height, dimId)

                    it.source.sendSuccess(
                        {
                            translatable(AIR_VALUES_DENSITY_MESSAGE, round(height*10.0)/10, density)
                        }, true
                    )

                    density.roundToInt()
                }
            )
            .then(literal("temperature")
                .executes {
                    val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                    val height = it.source.position.y
                    val dimId = it.source.level.dimensionId

                    val temperature = aero.getAirTemperatureForY(height, dimId)

                    it.source.sendSuccess(
                        {
                            translatable(AIR_VALUES_TEMPERATURE_MESSAGE, round(height*10.0)/10, temperature)
                        }, true
                    )

                    temperature.roundToInt()
                }
            )
            .then(literal("pressure")
                .executes {
                    val aero = it.source.level.shipObjectWorld.aerodynamicUtils
                    val height = it.source.position.y
                    val dimId = it.source.level.dimensionId

                    val pressure = aero.getAirPressureForY(height, dimId)

                    it.source.sendSuccess(
                        {
                            translatable(AIR_VALUES_PRESSURE_MESSAGE, round(height*10.0)/10, pressure)
                        }, true
                    )

                    pressure.roundToInt()
                }
            )
        )
    }
}
