package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.material.FlowingFluid
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.internal.physics.VsiFluidAbsorption
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.UUID

object DryCommand {
    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("dry")
            .requires { it.hasPermission(VSGameConfig.SERVER.Commands.dryShipCommandPerms) }
            .then(argument("ship", ShipArgument.ships())
                .executes { dryShip(it, ShipArgument.getShip(it, "ship")) }
                .then(argument("fluid", ResourceLocationArgument.id())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggestResource(
                            MassDatapackResolver.fluidTypesForClient().values.distinct().map(::ResourceLocation), builder)
                    }
                    .executes {
                        dryShip(it, ShipArgument.getShip(it, "ship"), ResourceLocationArgument.getId(it, "fluid"))
                    })))
    }

    fun dryShip(context: CommandContext<CommandSourceStack>, ship: Ship): Int = dryShip(context, ship, null)

    private fun dryShip(context: CommandContext<CommandSourceStack>, ship: Ship, fluid: ResourceLocation?): Int {
        val source = context.source
        val world = source.level.shipObjectWorld
        if (ship !is LoadedServerShip || !world.supportsFluidAbsorption) {
            source.sendFailure(Component.translatable("command.valkyrienskies.dry.unavailable"))
            return 0
        }
        val ids = if (fluid == null) emptyList() else {
            val registered = BuiltInRegistries.FLUID.getOptional(fluid).orElse(null) as? FlowingFluid
            val canonical = registered?.let { BuiltInRegistries.FLUID.getKey(it.source).toString() }
            val matching = MassDatapackResolver.fluidTypesForClient().filterValues { it == canonical }.keys.toList()
            if (matching.isEmpty()) {
                source.sendFailure(Component.translatable("command.valkyrienskies.dry.unknown_fluid", fluid.toString()))
                return 0
            }
            matching
        }
        val request = VsiFluidAbsorption(UUID.randomUUID(), ship.chunkClaimDimension, ship.id, 0, 0,
            0, 0, 0, ids, maximumDepth = 0, maximumCells = 0, maximumAmount = 0.0, clearBody = true)
        if (!world.submitFluidAbsorption(request)) {
            source.sendFailure(Component.translatable("command.valkyrienskies.dry.busy"))
            return 0
        }
        source.sendSuccess({
            if (fluid == null) Component.translatable("command.valkyrienskies.dry.queued_all")
            else Component.translatable("command.valkyrienskies.dry.queued_fluid", fluid.toString())
        }, true)
        return 1
    }
}
