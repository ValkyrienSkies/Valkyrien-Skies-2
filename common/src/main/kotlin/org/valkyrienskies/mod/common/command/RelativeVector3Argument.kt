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
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable
import java.util.concurrent.CompletableFuture

class RelativeVector3Argument : ArgumentType<RelativeVector3> {
    override fun parse(reader: StringReader): RelativeVector3 = RelativeVector3ArgumentParser().parse(reader, false)

    override fun <S> listSuggestions(
        context: CommandContext<S>?, builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions?>? {
        val reader = StringReader(builder.input)
        reader.cursor = builder.start

        val parser = RelativeVector3ArgumentParser()
        try {
            parser.parse(reader, true)
        } catch (_: CommandSyntaxException) {
        }

        // Reset cursor to fix suggestions
        reader.cursor = builder.start

        val nBuilder = builder.createOffset(reader.cursor)
        parser.suggestionProvider(nBuilder)

        return nBuilder.buildFuture()
    }

    override fun getExamples(): Collection<String> {
        return EXAMPLES
    }

    companion object {
        private val EXAMPLES: Collection<String> = listOf("(0 0 0)", "(~ ~ ~)", "(~0.5 ~1 ~-5)")

        private val DUMMY_EULER_ANGLES =
            RelativeVector3(RelativeValue(0.0, false), RelativeValue(0.0, false), RelativeValue(0.0, false))

        fun relativeVector3() = RelativeVector3Argument()

        fun getRelativeVector3(commandContext: CommandContext<CommandSourceStack?>, string: String?): RelativeVector3 {
            return commandContext.getArgument(
                string,
                RelativeVector3::class.java
            ) as RelativeVector3
        }

        private class RelativeVector3ArgumentParser {
            var suggestionProvider: (SuggestionsBuilder) -> Unit = {}

            fun parse(reader: StringReader, invokedByListSuggestions: Boolean): RelativeVector3 {
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
                                            RelativeValue(worldCoordinate.get(0.0), worldCoordinate.isRelative)
                                        val yawEulerAngle =
                                            RelativeValue(worldCoordinate2.get(0.0), worldCoordinate2.isRelative)
                                        val rollEulerAngle =
                                            RelativeValue(worldCoordinate3.get(0.0), worldCoordinate3.isRelative)

                                        suggest { builder ->
                                            builder.suggest(builder.remaining)
                                        }
                                        return RelativeVector3(pitchEulerAngle, yawEulerAngle, rollEulerAngle)
                                    }
                                } else {
                                    if (invokedByListSuggestions) {
                                        // Suggest ending with a ")"
                                        suggest { builder ->
                                            builder.suggest("${builder.remaining})")
                                        }
                                        throw SimpleCommandExceptionType(
                                            Component.translatable("Expected )")
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
