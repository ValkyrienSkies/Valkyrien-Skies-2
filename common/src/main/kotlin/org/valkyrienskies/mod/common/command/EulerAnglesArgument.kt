package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.coordinates.RotationArgument
import net.minecraft.commands.arguments.coordinates.WorldCoordinate
import net.minecraft.network.chat.TranslatableComponent
import java.util.concurrent.CompletableFuture

class EulerAnglesArgument : ArgumentType<EulerAngles> {
    override fun parse(reader: StringReader): EulerAngles = EulerAnglesArgumentParser().parse(reader, false)

    override fun <S> listSuggestions(
        context: CommandContext<S>?, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions?>? {
        val reader = StringReader(builder.input)
        reader.cursor = builder.start

        val startsWithAt = reader.canRead() && reader.peek() == '@'

        val parser = EulerAnglesArgumentParser()

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

    override fun getExamples(): Collection<String> {
        return EXAMPLES
    }

    companion object {
        private val EXAMPLES: Collection<String> =
            listOf("(0 0 0)", "(~ ~ ~)", "(~0.5 ~1 ~-5)")

        private val DUMMY_EULER_ANGLES =
            EulerAngles(EulerAngle(0.0, false), EulerAngle(0.0, false), EulerAngle(0.0, false))

        fun eulerAngles() = EulerAnglesArgument()

        fun getEulerAngles(commandContext: CommandContext<CommandSourceStack?>, string: String?): EulerAngles {
            return commandContext.getArgument(
                string,
                EulerAngles::class.java
            ) as EulerAngles
        }

        private class EulerAnglesArgumentParser {
            var suggestionProvider: (SuggestionsBuilder) -> Unit = {}

            fun parse(reader: StringReader, invokedByListSuggestions: Boolean): EulerAngles {
                val i: Int = reader.cursor
                if (!reader.canRead()) {
                    throw RotationArgument.ERROR_NOT_COMPLETE.createWithContext(reader)
                } else {
                    if (!reader.canRead()) {
                        if (invokedByListSuggestions) {
                            // Suggest setting rotation to 0
                            suggest { builder ->
                                builder.suggest("(0 0 0)")
                            }
                        } else {
                            return DUMMY_EULER_ANGLES
                        }
                    }

                    if (reader.canRead() && reader.peek() == '(') {
                        reader.skip()

                        if (!reader.canRead()) {
                            if (invokedByListSuggestions) {
                                // Suggest ending with 0 pitch 0 yaw and 0 roll
                                suggest { builder ->
                                    builder.suggest("${builder.remaining}0 0 0)")
                                }
                            } else {
                                return DUMMY_EULER_ANGLES
                            }
                        }

                        val worldCoordinate = WorldCoordinate.parseDouble(reader, false)

                        if (!reader.canRead()) {
                            if (invokedByListSuggestions) {
                                // Suggest ending with 0 yaw and 0 roll
                                suggest { builder ->
                                    builder.suggest("${builder.remaining} 0 0)")
                                }
                            } else {
                                return DUMMY_EULER_ANGLES
                            }
                        }

                        if (reader.canRead() && reader.peek() == ' ') {
                            reader.skip()

                            if (!reader.canRead()) {
                                if (invokedByListSuggestions) {
                                    // Suggest ending with 0 yaw and 0 roll
                                    suggest { builder ->
                                        builder.suggest("${builder.remaining}0 0)")
                                    }
                                } else {
                                    return DUMMY_EULER_ANGLES
                                }
                            }

                            val worldCoordinate2 = WorldCoordinate.parseDouble(reader, false)

                            if (!reader.canRead()) {
                                if (invokedByListSuggestions) {
                                    // Suggest ending with 0 roll
                                    suggest { builder ->
                                        builder.suggest("${builder.remaining} 0)")
                                    }
                                } else {
                                    return DUMMY_EULER_ANGLES
                                }
                            }

                            if (reader.canRead() && reader.peek() == ' ') {
                                reader.skip()
                                if (!reader.canRead()) {
                                    if (invokedByListSuggestions) {
                                        // Suggest ending with 0 roll
                                        suggest { builder ->
                                            builder.suggest("${builder.remaining}0)")
                                        }
                                    } else {
                                        return DUMMY_EULER_ANGLES
                                    }
                                }
                                val worldCoordinate3 = WorldCoordinate.parseDouble(reader, false)
                                if (reader.canRead()) {
                                    if (reader.peek() == ')') {
                                        reader.skip()
                                        val pitchEulerAngle =
                                            EulerAngle(worldCoordinate.get(0.0), worldCoordinate.isRelative)
                                        val yawEulerAngle =
                                            EulerAngle(worldCoordinate2.get(0.0), worldCoordinate2.isRelative)
                                        val rollEulerAngle =
                                            EulerAngle(worldCoordinate3.get(0.0), worldCoordinate3.isRelative)

                                        suggest { builder ->
                                            builder.suggest(builder.remaining)
                                        }
                                        return EulerAngles(pitchEulerAngle, yawEulerAngle, rollEulerAngle)
                                    }
                                } else {
                                    if (invokedByListSuggestions) {
                                        // Suggest ending with a ")"
                                        suggest { builder ->
                                            builder.suggest("${builder.remaining})")
                                        }
                                        throw SimpleCommandExceptionType(
                                            TranslatableComponent("Expected )")
                                        ).createWithContext(reader)
                                    } else {
                                        return DUMMY_EULER_ANGLES
                                    }
                                }
                            }
                        }

                        if (!reader.canRead()) {
                            reader.cursor = i
                            throw RotationArgument.ERROR_NOT_COMPLETE.createWithContext(reader)
                        }
                    }
                    reader.cursor = i
                    throw RotationArgument.ERROR_NOT_COMPLETE.createWithContext(reader)
                }
            }

            private fun suggest(builder: (SuggestionsBuilder) -> Unit) {
                suggestionProvider = { builder(it) }
            }
        }
    }
}
