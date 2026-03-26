package org.valkyrienskies.mod.common.command.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.command.shipWorld
import java.util.concurrent.CompletableFuture

open class ShipArgument private constructor(val selectorOnly: Boolean) : ArgumentType<ShipSelector> {
    private val EXAMPLES = listOf("the-mogus", "@v", "@v[slug=the-mogus]")

    /**
     * In vanilla, the only classes [S] will be are impls of [SharedSuggestionProvider].
     * Another mod might violate this, but hopefully null checks will handle it
     */
    override fun <S : Any> listSuggestions(
        context: CommandContext<S>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val reader = StringReader(builder.input)
        reader.cursor = builder.start

        val startsWithAt = reader.canRead() && reader.peek() == '@'

        // If context.source isn't an impl of SharedSuggestionProvider,
        // and is null, the parser will just not suggest anything
        val parser = ShipArgumentParser(context.source as? SharedSuggestionProvider, selectorOnly)

        try {
            parser.parse(reader, true)
        } catch (_: CommandSyntaxException) {

        }

        // Reset cursor to fix suggestions
        if (!startsWithAt) {
            reader.cursor = builder.start
        }

        val nBuilder = builder.createOffset(reader.cursor)
        parser.suggestionProvider(nBuilder)

        return nBuilder.buildFuture()
    }

    override fun parse(reader: StringReader): ShipSelector =
        ShipArgumentParser(null, selectorOnly).parse(reader, false)

    companion object {

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

            val fromLoadedShips = selector.select(shipWorld.loadedShips)
            val fromLoadedShipIds = fromLoadedShips.map { it.id }.toSet()

            val fromUnloadedShips = selector.select(shipWorld.allShips)

            // Return loaded ships and unloaded ships, do not return a loaded ship twice
            return fromLoadedShips + (fromUnloadedShips.filter { !fromLoadedShipIds.contains(it.id) })
        }

        /**
         * @return Can return either a loaded ship or an unloaded ship
         */
        @JvmStatic
        fun getShip(context: CommandContext<CommandSourceStack>, argName: String): Ship {
            val selector = context.getArgument(argName, ShipSelector::class.java)


            // First attempt to return a loaded ship
            val loadedShips = selector.select(context.source.shipWorld.loadedShips)
            if (loadedShips.size == 1) return loadedShips.first()

            // Then try to return an unloaded ship
            val r = selector.select(context.source.shipWorld.allShips)
            if (r.isEmpty()) throw ERROR_NO_SHIP_FOUND
            if (r.size == 1) return r.first() else throw ERROR_MANY_SHIP_FOUND
        }

        private val ERROR_NO_SHIP_FOUND = CommandRuntimeException(Component.translatable("argument.valkyrienskies.ship.no_found"))
        private val ERROR_MANY_SHIP_FOUND =
            CommandRuntimeException(Component.translatable("argument.valkyrienskies.ship.multiple_found"))
    }

    override fun getExamples(): Collection<String> = EXAMPLES
}
