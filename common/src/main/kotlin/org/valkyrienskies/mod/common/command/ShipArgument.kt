package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.TranslatableComponent
import org.valkyrienskies.core.api.ships.Ship
import java.util.concurrent.CompletableFuture

object ShipArgument : ArgumentType<ShipSelector> {
    private val EXAMPLES = listOf("the-mogus", "@v", "@v[slug=mogus]", "2387623827441")

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> = if (context.source is CommandSourceStack) {
        val reader = StringReader(builder.input)
        reader.cursor = builder.start

        val parser = ShipArgumentParser(context.source as CommandSourceStack)
        try {
            parser.parse(reader)
        } catch (_: CommandSyntaxException) {

        }

        val nBuilder = builder.createOffset(reader.cursor)
        parser.suggestionProvider(nBuilder)

        nBuilder.buildFuture()
    } else super.listSuggestions(context, builder)

    override fun parse(reader: StringReader): ShipSelector =
        ShipArgumentParser(null).parse(reader)

    fun getShips(context: CommandContext<CommandSourceStack>, argName: String): Set<Ship> {
        val selector = context.getArgument(argName, ShipSelector::class.java)

        return selector.select(context.source.shipWorld.allShips)
    }

    fun getShip(context: CommandContext<CommandSourceStack>, argName: String): Ship {
        val selector = context.getArgument(argName, ShipSelector::class.java)
        val r = selector.select(context.source.shipWorld.allShips)

        if (r.isEmpty()) throw ERROR_NO_SHIP_FOUND
        if (r.size == 1) return r.first() else throw ERROR_MANY_SHIP_FOUND
    }

    private val ERROR_NO_SHIP_FOUND = CommandRuntimeException(TranslatableComponent("argument.ship.no_found"))
    private val ERROR_MANY_SHIP_FOUND = CommandRuntimeException(TranslatableComponent("argument.ship.multiple_found"))
}
