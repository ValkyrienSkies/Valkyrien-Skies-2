package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.network.chat.TranslatableComponent
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource
import java.util.concurrent.CompletableFuture

class ShipArgument private constructor(val selectorOnly: Boolean) : ArgumentType<ShipSelector> {
    private val EXAMPLES = listOf("the-mogus", "@v", "@v[slug=the-mogus]")

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> = if (context.source is VSCommandSource) {
        val reader = StringReader(builder.input)
        reader.cursor = builder.start

        val parser = ShipArgumentParser(context.source as VSCommandSource, selectorOnly)

        try {
            parser.parse(reader)
        } catch (_: CommandSyntaxException) {

        }

        val nBuilder = builder.createOffset(reader.cursor)
        parser.suggestionProvider(nBuilder)

        nBuilder.buildFuture()
    } else super.listSuggestions(context, builder)

    override fun parse(reader: StringReader): ShipSelector =
        ShipArgumentParser(null, selectorOnly).parse(reader)

    companion object {

        fun selectorOnly(): ShipArgument = ShipArgument(true)
        fun ships(): ShipArgument = ShipArgument(false)

        fun <S : VSCommandSource> getShips(context: CommandContext<S>, argName: String): Set<Ship> {
            val selector = context.getArgument(argName, ShipSelector::class.java)

            return selector.select(context.source.shipWorld.allShips)
        }

        fun <S : VSCommandSource> getShip(context: CommandContext<S>, argName: String): Ship {
            val selector = context.getArgument(argName, ShipSelector::class.java)
            val r = selector.select(context.source.shipWorld.allShips)

            if (r.isEmpty()) throw ERROR_NO_SHIP_FOUND
            if (r.size == 1) return r.first() else throw ERROR_MANY_SHIP_FOUND
        }

        private val ERROR_NO_SHIP_FOUND = CommandRuntimeException(TranslatableComponent("argument.ship.no_found"))
        private val ERROR_MANY_SHIP_FOUND =
            CommandRuntimeException(TranslatableComponent("argument.ship.multiple_found"))
    }

    override fun getExamples(): Collection<String> = EXAMPLES
}
