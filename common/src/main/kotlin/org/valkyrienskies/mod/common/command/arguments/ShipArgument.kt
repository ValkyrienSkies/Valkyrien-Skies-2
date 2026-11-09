package org.valkyrienskies.mod.common.command.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.command.shipWorld
import java.util.concurrent.CompletableFuture

class ShipArgument private constructor(val selectorOnly: Boolean) : ArgumentType<ShipSelector?> {
    @Throws(CommandSyntaxException::class)
    override fun parse(pReader: StringReader): ShipSelector {
        val entityselectorparser = ShipSelectorParser(null, pReader)
        return entityselectorparser.parse(selectorOnly)
    }

    /**
     * In vanilla, the only classes [S] will be are impls of [SharedSuggestionProvider].
     * Another mod might violate this, but hopefully null checks will handle it
     */
    override fun <S> listSuggestions(
        pContext: CommandContext<S>, pBuilder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {

        val reader = StringReader(pBuilder.input)
        reader.cursor = getSuggestionParseStart(pBuilder)

        val startsWithAt = reader.canRead() && reader.peek() == '@'

        // If context.source isn't an impl of SharedSuggestionProvider,
        // and is null, the parser will just not suggest anything
        val parser = ShipSelectorParser(pContext.source as? SharedSuggestionProvider, reader)

        try {
            parser.parse(selectorOnly)
        } catch (_: CommandSyntaxException) {

        }

        // Reset cursor to fix suggestions
        if (!startsWithAt) {
            reader.cursor = pBuilder.start
        }

        val nBuilder = pBuilder.createOffset(reader.cursor)
        return parser.suggestionProvider(nBuilder)
    }

    private fun getSuggestionParseStart(builder: SuggestionsBuilder): Int {
        val selectorStart = builder.input.lastIndexOf("@v")
        if (selectorStart == -1) {
            return builder.start
        }

        val previousWhitespace = builder.input
            .substring(0, builder.start.coerceAtMost(builder.input.length))
            .indexOfLast { it.isWhitespace() }

        return if (selectorStart > previousWhitespace) {
            selectorStart
        } else {
            builder.start
        }
    }

    override fun getExamples(): List<String> {
        return EXAMPLES
    }

    companion object {
        private val EXAMPLES = listOf("the-mogus", "@v", "@v[slug=the-mogus]")

        @JvmStatic
        fun selectorOnly(): ShipArgument = ShipArgument(true)

        @JvmStatic
        fun ships(): ShipArgument = ShipArgument(false)

        /**
         * @return Can return either a loaded ship or an unloaded ship
         */
        @JvmStatic
        fun getShips(context: CommandContext<CommandSourceStack>, argName: String): Set<Ship> {
            val selector = context.getArgument(argName, ShipSelector::class.java)

            val shipWorld = context.source.shipWorld

            val fromLoadedShips = selector.select(context.source, shipWorld.loadedShips)
            val fromLoadedShipIds = fromLoadedShips.map { it.id }.toSet()

            val fromUnloadedShips = selector.select(context.source, shipWorld.allShips)

            // Return loaded ships and unloaded ships, do not return a loaded ship twice
            return (fromLoadedShips + (fromUnloadedShips.filter { !fromLoadedShipIds.contains(it.id) })).toSet()
        }

        /**
         * @return Can return either a loaded ship or an unloaded ship
         */
        @JvmStatic
        fun getShip(context: CommandContext<CommandSourceStack>, argName: String): Ship {
            val selector = context.getArgument(argName, ShipSelector::class.java)


            // First attempt to return a loaded ship
            val loadedShips = selector.select(context.source, context.source.shipWorld.loadedShips)
            if (loadedShips.count() == 1) return loadedShips.first()

            // Then try to return an unloaded ship
            val r = selector.select(context.source, context.source.shipWorld.allShips)
            if (r.none()) throw ERROR_NO_SHIP_FOUND
            if (r.count() == 1) return r.first() else throw ERROR_MANY_SHIP_FOUND
        }

        private val ERROR_NO_SHIP_FOUND = SimpleCommandExceptionType(
            Component.translatable("argument.valkyrienskies.ship.no_found")
        ).create()
        private val ERROR_MANY_SHIP_FOUND = SimpleCommandExceptionType(
            Component.translatable("argument.valkyrienskies.ship.multiple_found")
        ).create()
    }
}
