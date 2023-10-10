package org.valkyrienskies.mod.common.command

import com.google.gson.JsonObject
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.synchronization.ArgumentSerializer
import net.minecraft.network.FriendlyByteBuf
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

        // This can be a valid ship argument in the following cases:
        // * The reader is empty, so we can suggest @v
        // * The reader is only 1 character long, and is "@", so we can suggest @v
        // * The reader is 2 characters long, and is "@v", so we can suggest @v
        var canThisBeShipArgument = !reader.canRead() || (reader.canRead() && reader.peek() == '@')
        if (reader.canRead(2)) {
            canThisBeShipArgument = canThisBeShipArgument && reader.peek(1) == 'v'
        }

        val parser = ShipArgumentParser(context.source as VSCommandSource, selectorOnly)

        try {
            parser.parse(reader, true)
        } catch (_: CommandSyntaxException) {

        }

        // Reset cursor to fix suggestions
        if (!canThisBeShipArgument) {
            // Don't suggest a ship argument if the contents of reader cannot be a ship argument
            reader.cursor = builder.start
            super.listSuggestions(context, builder)
        } else {
            val nBuilder = builder.createOffset(reader.cursor)
            parser.suggestionProvider(nBuilder)
            nBuilder.buildFuture()
        }
    } else super.listSuggestions(context, builder)

    override fun parse(reader: StringReader): ShipSelector =
        ShipArgumentParser(null, selectorOnly).parse(reader, false)

    companion object {

        fun selectorOnly(): ShipArgument = ShipArgument(true)
        fun ships(): ShipArgument = ShipArgument(false)

        /**
         * @return Can return either a loaded ship or an unloaded ship
         */
        fun <S : VSCommandSource> getShips(context: CommandContext<S>, argName: String): Set<Ship> {
            val selector = context.getArgument(argName, ShipSelector::class.java)

            val fromLoadedShips = selector.select(context.source.shipWorld.loadedShips)
            val fromLoadedShipIds = fromLoadedShips.map { it.id }.toSet()

            val fromUnloadedShips = selector.select(context.source.shipWorld.allShips)

            // Return loaded ships and unloaded ships, do not return a loaded ship twice
            return fromLoadedShips + (fromUnloadedShips.filter { !fromLoadedShipIds.contains(it.id) })
        }

        /**
         * @return Can return either a loaded ship or an unloaded ship
         */
        fun <S : VSCommandSource> getShip(context: CommandContext<S>, argName: String): Ship {
            val selector = context.getArgument(argName, ShipSelector::class.java)

            // First attempt to return a loaded ship
            val loadedShips = selector.select(context.source.shipWorld.loadedShips)
            if (loadedShips.size == 1) return loadedShips.first()

            // Then try to return an unloaded ship
            val r = selector.select(context.source.shipWorld.allShips)
            if (r.isEmpty()) throw ERROR_NO_SHIP_FOUND
            if (r.size == 1) return r.first() else throw ERROR_MANY_SHIP_FOUND
        }

        private val ERROR_NO_SHIP_FOUND = CommandRuntimeException(TranslatableComponent("argument.valkyrienskies.ship.no_found"))
        private val ERROR_MANY_SHIP_FOUND =
            CommandRuntimeException(TranslatableComponent("argument.valkyrienskies.ship.multiple_found"))
    }

    override fun getExamples(): Collection<String> = EXAMPLES

    object Serializer : ArgumentSerializer<ShipArgument> {
        override fun serializeToNetwork(arg: ShipArgument, buf: FriendlyByteBuf) {
            buf.writeBoolean(arg.selectorOnly)
        }

        override fun deserializeFromNetwork(buf: FriendlyByteBuf): ShipArgument {
            return ShipArgument(buf.readBoolean())
        }

        override fun serializeToJson(arg: ShipArgument, json: JsonObject) {
            json.addProperty("selectorOnly", arg.selectorOnly)
        }
    }
}
