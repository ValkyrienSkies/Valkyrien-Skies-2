package org.valkyrienskies.mod.common.command.arguments

import com.google.common.collect.Maps
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.advancements.critereon.WrappedMinMaxBounds
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.common.command.shipWorld
import java.util.function.Predicate

typealias Modifier = (ShipSelectorParser) -> Unit

object ContraptionSelectorOptions {
    private val OPTIONS: MutableMap<String?, Option?> = Maps.newHashMap<String?, Option?>()
    val ERROR_UNKNOWN_OPTION: DynamicCommandExceptionType = DynamicCommandExceptionType { obj: Any? ->
        Component.translatable(
            "argument.valkyrienskies.ship.unknown_option", obj
        )
    }
    val ERROR_INAPPLICABLE_OPTION: DynamicCommandExceptionType = DynamicCommandExceptionType { obj: Any? ->
        Component.translatable(
            "argument.valkyrienskies.ship.options.inapplicable", obj
        )
    }

    val ERROR_VALUE_NEGATIVE: SimpleCommandExceptionType = SimpleCommandExceptionType(
        Component.translatable("argument.valkyrienskies.ship.options.value_negative")
    )

    fun register(
        pId: String?, pHandler: Modifier, pPredicate: Predicate<ShipSelectorParser>, autocomplete: ((SharedSuggestionProvider?, SuggestionsBuilder) -> Unit)? = null
    ) {
        OPTIONS[pId] = Option(pHandler, pPredicate, autocomplete = autocomplete)
    }

    fun bootStrap() {
        if (OPTIONS.isEmpty()) {
            register(
                "slug", { parser: ShipSelectorParser ->
                    val i = parser.reader.cursor
                    val flag = parser.shouldInvertValue()
                    val s = parser.reader.readString()
                    // Checks if we already have a slug=... or a slug=!...
                    if (((parser.slug != null) && !flag) || ((parser.notSlug != null) && flag)) {
                        parser.reader.cursor = i
                        throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.reader, "slug")
                    } else {
                        if (flag) {
                            parser.notSlug = s
                        } else {
                            parser.slug = s
                        }
                    }
                }, { parser: ShipSelectorParser -> (parser.slug == null) || (parser.notSlug == null) },
                { parser, builder ->
                    parser?.let { p ->
                        p.shipWorld.allShips
                            .mapNotNull { it.slug }
                            .filter { it.startsWith(builder.remaining) }
                            .forEach { builder.suggest(it) }
                    }
                }
            )
            register(
                "id", { parser: ShipSelectorParser ->
                    parser.id = parser.reader.readLong()
                }, { parser: ShipSelectorParser -> parser.id == null },
                { parser, builder ->
                    parser?.let { p ->
                        p.shipWorld.allShips
                        .mapNotNull { it.id.toString() }
                        .filter { it.startsWith(builder.remaining) }
                        .forEach { builder.suggest(it) } }
                }
            )
            register(
                "distance", { parser: ShipSelectorParser ->
                    val i = parser.reader.cursor
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    if ((`minmaxbounds$doubles`.getMin() == null || !(`minmaxbounds$doubles`.getMin()!! < 0.0)) && (`minmaxbounds$doubles`.getMax() == null || !(`minmaxbounds$doubles`.getMax()!! < 0.0))) {
                        parser.distance = `minmaxbounds$doubles`
                        parser.needsSpecificLevel = true
                    } else {
                        parser.reader.cursor = i
                        throw ERROR_VALUE_NEGATIVE.createWithContext(parser.reader)
                    }
                }, { parser: ShipSelectorParser -> parser.distance.isAny }
            )
            register(
                "x", { parser: ShipSelectorParser ->
                    parser.needsSpecificLevel = true
                    parser.x = parser.reader.readDouble()
                }, { parser: ShipSelectorParser -> parser.x == null }
            )
            register(
                "y", { parser: ShipSelectorParser ->
                    parser.needsSpecificLevel = true
                    parser.y = parser.reader.readDouble()
                }, { parser: ShipSelectorParser -> parser.y == null }
            )
            register(
                "z", { parser: ShipSelectorParser ->
                    parser.needsSpecificLevel = true
                    parser.z = parser.reader.readDouble()
                }, { parser: ShipSelectorParser -> parser.z == null }
            )
            register(
                "dx", { parser: ShipSelectorParser ->
                    parser.needsSpecificLevel = true
                    parser.deltaX = parser.reader.readDouble()
                }, { parser: ShipSelectorParser -> parser.deltaX == null }
            )
            register(
                "dy", { parser: ShipSelectorParser ->
                    parser.needsSpecificLevel = true
                    parser.deltaY = parser.reader.readDouble()
                }, { parser: ShipSelectorParser -> parser.deltaY == null }
            )
            register(
                "dz", { parser: ShipSelectorParser ->
                    parser.needsSpecificLevel = true
                    parser.deltaZ = parser.reader.readDouble()
                }, { parser: ShipSelectorParser -> parser.deltaZ == null }
            )
            register(
                "x_rotation", { parser: ShipSelectorParser ->
                    parser.rotX = WrappedMinMaxBounds.fromReader(
                        parser.reader, true
                    ) { f: Float -> Mth.wrapDegrees(f) }
                }, { parser: ShipSelectorParser -> parser.rotX === WrappedMinMaxBounds.ANY }
            )
            register(
                "y_rotation", { parser: ShipSelectorParser ->
                    parser.rotY = WrappedMinMaxBounds.fromReader(
                        parser.reader, true
                    ) { f: Float -> Mth.wrapDegrees(f) }
                }, { parser: ShipSelectorParser -> parser.rotY === WrappedMinMaxBounds.ANY }
            )
            register(
                "z_rotation", { parser: ShipSelectorParser ->
                    parser.rotZ = WrappedMinMaxBounds.fromReader(
                        parser.reader, true
                    ) { f: Float -> Mth.wrapDegrees(f) }
                }, { parser: ShipSelectorParser -> parser.rotZ === WrappedMinMaxBounds.ANY }
            )

            register(
                "x_velocity", { parser: ShipSelectorParser ->
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    parser.velocityX = `minmaxbounds$doubles`
                }, { parser: ShipSelectorParser -> parser.velocityX === MinMaxBounds.Doubles.ANY }
            )
            register(
                "y_velocity", { parser: ShipSelectorParser ->
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    parser.velocityY = `minmaxbounds$doubles`
                }, { parser: ShipSelectorParser -> parser.velocityY === MinMaxBounds.Doubles.ANY }
            )
            register(
                "z_velocity", { parser: ShipSelectorParser ->
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    parser.velocityZ = `minmaxbounds$doubles`
                }, { parser: ShipSelectorParser -> parser.velocityZ === MinMaxBounds.Doubles.ANY }
            )

            register(
                "velocity", { parser: ShipSelectorParser ->
                    val i = parser.reader.cursor
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    if ((`minmaxbounds$doubles`.getMin() == null || (`minmaxbounds$doubles`.getMin()!! >= 0.0)) && (`minmaxbounds$doubles`.getMax() == null || (`minmaxbounds$doubles`.getMax()!! >= 0.0))) {
                        parser.velocity = `minmaxbounds$doubles`
                    } else {
                        parser.reader.cursor = i
                        throw ERROR_VALUE_NEGATIVE.createWithContext(parser.reader)
                    }
                }, { parser: ShipSelectorParser -> parser.velocity === MinMaxBounds.Doubles.ANY }
            )

            register(
                "x_omega", { parser: ShipSelectorParser ->
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    parser.omegaX = `minmaxbounds$doubles`
                }, { parser: ShipSelectorParser -> parser.omegaX === MinMaxBounds.Doubles.ANY }
            )
            register(
                "y_omega", { parser: ShipSelectorParser ->
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    parser.omegaY = `minmaxbounds$doubles`
                }, { parser: ShipSelectorParser -> parser.omegaY === MinMaxBounds.Doubles.ANY }
            )
            register(
                "z_omega", { parser: ShipSelectorParser ->
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    parser.omegaZ = `minmaxbounds$doubles`
                }, { parser: ShipSelectorParser -> parser.omegaZ === MinMaxBounds.Doubles.ANY }
            )

            register(
                "omega", { parser: ShipSelectorParser ->
                    val i = parser.reader.cursor
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    if ((`minmaxbounds$doubles`.getMin() == null || (`minmaxbounds$doubles`.getMin()!! >= 0.0)) && (`minmaxbounds$doubles`.getMax() == null || (`minmaxbounds$doubles`.getMax()!! >= 0.0))) {
                        parser.omega = `minmaxbounds$doubles`
                    } else {
                        parser.reader.cursor = i
                        throw ERROR_VALUE_NEGATIVE.createWithContext(parser.reader)
                    }
                }, { parser: ShipSelectorParser -> parser.omega === MinMaxBounds.Doubles.ANY }
            )

            register(
                "size", { parser: ShipSelectorParser ->
                    val i = parser.reader.cursor
                    val `minmaxbounds$ints` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    if ((`minmaxbounds$ints`.getMin() == null || (`minmaxbounds$ints`.getMin()!! >= 0)) && (`minmaxbounds$ints`.getMax() == null || (`minmaxbounds$ints`.getMax()!! >= 0))) {
                        parser.size = `minmaxbounds$ints`
                    } else {
                        parser.reader.cursor = i
                        throw ERROR_VALUE_NEGATIVE.createWithContext(parser.reader)
                    }
                }, { parser: ShipSelectorParser -> parser.size === MinMaxBounds.Doubles.ANY }
            )

            register(
                "static", { parser: ShipSelectorParser ->
                    val isStatic = parser.reader.readBoolean()

                    parser.isStatic = isStatic
                }, { parser: ShipSelectorParser -> parser.isStatic == null }
            )

            register(
                "mass", { parser: ShipSelectorParser ->
                    val `minmaxbounds$doubles` = MinMaxBounds.Doubles.fromReader(parser.reader)
                    parser.mass = `minmaxbounds$doubles`
                }, { parser: ShipSelectorParser -> parser.mass === MinMaxBounds.Doubles.ANY }
            )


            register(
                "limit", { parser: ShipSelectorParser ->
                    val i = parser.reader.cursor
                    val j = parser.reader.readInt()
                    if (j < 1) {
                        parser.reader.cursor = i
                        throw ERROR_VALUE_NEGATIVE.createWithContext(parser.reader)
                    } else {
                        parser.amountLimit = j
                    }
                },
                { parser: ShipSelectorParser -> parser.amountLimit == 0 }
            )
            register(
                "sort", { parser: ShipSelectorParser ->
                    val i = parser.reader.cursor
                    val s = parser.reader.readUnquotedString()

                    val biconsumer: (Vec3, Sequence<Ship>) -> Sequence<Ship>
                    when (s) {
                        "arbitrary" -> biconsumer = ShipSelector.ORDER_ARBITRARY
                        "nearest" -> biconsumer = ShipSelectorParser.ORDER_NEAREST
                        "furthest" -> biconsumer = ShipSelectorParser.ORDER_FURTHEST
                        "random" -> biconsumer = ShipSelectorParser.ORDER_RANDOM
                        else -> {
                            parser.reader.cursor = i
                            throw ERROR_UNKNOWN_OPTION.createWithContext(parser.reader, s)
                        }
                    }

                    parser.customSort = biconsumer
                },
                { parser: ShipSelectorParser -> parser.customSort == ShipSelector.ORDER_ARBITRARY },
                { provider, builder ->
                    builder.suggest("arbitrary")
                    builder.suggest("nearest")
                    builder.suggest("furthest")
                    builder.suggest("random")
                }
            )
        }
    }

    @Throws(CommandSyntaxException::class)
    fun get(pParser: ShipSelectorParser, pId: String?, pCursor: Int): Modifier {
        val `entityselectoroptions$option` = OPTIONS[pId]
        if (`entityselectoroptions$option` != null) {
            if (`entityselectoroptions$option`.canUse.test(pParser)) {
                return `entityselectoroptions$option`.modifier
            } else {
                throw ERROR_INAPPLICABLE_OPTION.createWithContext(pParser.reader, pId)
            }
        } else {
            pParser.reader.cursor = pCursor
            throw ERROR_UNKNOWN_OPTION.createWithContext(pParser.reader, pId)
        }
    }

    /**
     * Suggests the names for selector options, e.g. slug=, id=, etc
     */
    fun suggestNames(pParser: ShipSelectorParser, pBuilder: SuggestionsBuilder) {
        val s = pBuilder.remaining.lowercase()

        for (entry in OPTIONS.entries) {
            if ((entry.value)!!.canUse.test(pParser) && entry.key!!.lowercase().startsWith(s)) {
                pBuilder.suggest(entry.key as String + "=", optionalTranslation("argument.$MOD_ID.ship.options.${entry.key}"))
            }
        }
    }

    fun optionalTranslation(key: String): Component? {
        return if (Language.getInstance().has(key))
            Component.translatable(key)
        else
            null
    }

    /**
     * Suggests a value for an option if that option registered with an autocomplete provider.
     * For example, if the user has typed slug=, this function will suggest the actual slugs.
     */
    fun suggestOptionValue(provider: SharedSuggestionProvider?, pBuilder: SuggestionsBuilder): SuggestionsBuilder? {
        val equalsIndex = pBuilder.input.lastIndexOf('=', (pBuilder.start - 1).coerceAtLeast(0))
        if (equalsIndex == -1) {
            return null
        }

        val optionStart = maxOf(
            pBuilder.input.lastIndexOf('[', equalsIndex),
            pBuilder.input.lastIndexOf(',', equalsIndex)
        ) + 1
        val optionName = pBuilder.input.substring(optionStart, equalsIndex).trim().lowercase()

        for (entry in OPTIONS.entries) {
            val valueAutocomplete = entry.value!!.autocomplete ?: continue

            if (optionName == entry.key) {
                val offset = equalsIndex + 1
                val builder = pBuilder.createOffset(offset)

                valueAutocomplete(provider, builder)
                return builder
            }
        }
        return null
    }
    
    /**
     * Represents a ship selector argument
     * @param modifier A lambda for reading/parsing the current command state and changing the selector values for this option
     * @param canUse A predicate to run to see if this option is available to be used. Usually used to prevent the user from specifying the same option twice.
     * @param autocomplete An optional lambda to have autocomplete for this options value, aka for suggesting nearby ship slugs.
     */
    @JvmRecord
    internal data class Option(
        val modifier: Modifier, val canUse: Predicate<ShipSelectorParser>, val autocomplete: ((SharedSuggestionProvider?, SuggestionsBuilder) -> Unit)? = null
    )
}
