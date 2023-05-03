package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.network.chat.TranslatableComponent
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource

class ShipArgumentParser(private val source: VSCommandSource?, private var selectorOnly: Boolean) {
    var suggestionProvider: (SuggestionsBuilder) -> Unit = {}
    var slug: String? = null
    var limit: Int? = null
    var id: ShipId? = null

    fun parse(reader: StringReader): ShipSelector {
        val start = reader.cursor

        suggestSelectorOrSlug()

        if (!reader.canRead())
            throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(reader)

        // Read the selector type
        if (reader.read() == '@') {
            reader.expectR('v')

            suggestOpenOptions()
            if (reader.canRead() && reader.read() == '[') {

                // Read the selector arguments ex @v[slug=mogus,limit=1]
                while (reader.canRead() && reader.peek() != ']') {
                    suggestOptions()
                    reader.skipWhitespace()

                    val i = reader.cursor
                    val s = reader.readString()

                    if (!isOption(s)) {
                        reader.cursor = i
                        throw ERROR_UNKNOWN_OPTION.createWithContext(reader, s)
                    }

                    reader.skipWhitespace()

                    suggestEquals()

                    if (!reader.canRead() || reader.peek() != '=') {
                        throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, s)
                    }

                    reader.skip()
                    reader.skipWhitespace()

                    suggestionsOfOption(s)

                    reader.skipWhitespace()
                    if (reader.canRead())
                        parseOption(s, reader)
                    else throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, s)
                    reader.skipWhitespace()

                    suggestOptionsNextOrClose()
                    reader.skipWhitespace()
                }

                if (!reader.canRead() || reader.read() != ']')
                    throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader)

                suggest { _, _ -> }
            }
        } else if (!selectorOnly) {
            suggestionsOfOption("slug")
            // Reset cursor
            reader.cursor = start
            slug = reader.readUnquotedString()
        }

        return ShipSelector(slug, id, limit ?: Int.MAX_VALUE)
    }

    private fun isOption(s: String): Boolean = when (s) {
        "slug", "limit", "id" -> true
        else -> false
    }

    // TODO keep a dynamic list of options...
    private fun suggestOptions() = suggest { builder, source ->
        builder.suggest("slug=")
        builder.suggest("limit=")
        builder.suggest("id=")
    }

    private fun suggestSelectorOrSlug() = suggest { builder, source ->
        builder.suggest("@v")
        if (!selectorOnly) {
            source.shipWorld.allShips
                .mapNotNull { it.slug }
                .filter { it.startsWith(builder.remaining) }
                .forEach { builder.suggest(it) }
        }
    }

    fun suggestionsOfOption(option: String): Unit = when (option) {
        "slug" ->
            suggest { builder, source ->
                source.shipWorld.allShips
                    .mapNotNull { it.slug }
                    .filter { it.startsWith(builder.remaining) }
                    .forEach { builder.suggest(it) }
            }

        "limit" -> {}
        else -> throw ERROR_UNKNOWN_OPTION.create(option)
    }

    fun parseOption(option: String, reader: StringReader): Unit = when (option) {
        "slug" -> {
            val start = reader.cursor
            val slug = reader.readUnquotedString()

            if (source?.shipWorld?.allShips?.any { it.slug == slug } == false) {
                reader.cursor = start
                throw ERROR_INVALID_SLUG_OR_ID.create()
            }

            this.slug = slug
        }

        "limit" -> limit = reader.readInt()
        else -> throw ERROR_UNKNOWN_OPTION.create(option)
    }

    private fun suggestEquals() = suggest { builder, _ ->
        builder.suggest('='.toString())
    }

    private fun suggestOpenOptions() = suggest { builder, _ ->
        builder.suggest("[")
    }

    private fun suggestOptionsNextOrClose() = suggest { builder, _ ->
        builder.suggest(",")
        builder.suggest("]")
    }

    private fun suggest(builder: (SuggestionsBuilder, VSCommandSource) -> Unit) {
        if (source != null) suggestionProvider = { builder(it, source) }
    }

    private fun StringReader.expectR(s: Char) {
        suggest { builder, source -> builder.suggest(s.toString()) }
        this.expect(s)
    }

    companion object {
        val ERROR_MISSING_SELECTOR_TYPE =
            SimpleCommandExceptionType(TranslatableComponent("argument.ship.selector.missing"))
        val ERROR_INVALID_SLUG_OR_ID =
            SimpleCommandExceptionType(TranslatableComponent("argument.ship.invalid"))
        val ERROR_EXPECTED_END_OF_OPTIONS =
            SimpleCommandExceptionType(TranslatableComponent("argument.ship.options.unterminated"))
        val ERROR_EXPECTED_OPTION_VALUE =
            DynamicCommandExceptionType { TranslatableComponent("argument.ship.options.valueless", it) }
        val ERROR_UNKNOWN_OPTION =
            DynamicCommandExceptionType { TranslatableComponent("argument.entity.options.unknown", it) }
    }
}
