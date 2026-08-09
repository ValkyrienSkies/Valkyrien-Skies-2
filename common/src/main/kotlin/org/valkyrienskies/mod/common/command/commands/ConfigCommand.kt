package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.literal as textLiteral
import net.minecraft.network.chat.Component.translatable
import org.valkyrienskies.mod.common.config.ConfigType
import org.valkyrienskies.mod.common.config.VSConfigUpdater
import java.util.concurrent.CompletableFuture

object ConfigCommand {
    private const val RELOAD_MESSAGE = "command.valkyrienskies.config.reload"
    private const val SAVE_MESSAGE = "command.valkyrienskies.config.save"

    private const val REQUIRED_PERMISSION_LEVEL = 2

    private val CONFIG_TYPES = linkedMapOf(
        "core" to ConfigType.CORE_SERVER,
        "server" to ConfigType.SERVER,
        "common" to ConfigType.COMMON,
        "client" to ConfigType.CLIENT
    )

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        val config = literal("config")
            .requires { it.hasPermission(REQUIRED_PERMISSION_LEVEL) }
            .then(literal("reload")
                .executes {
                    VSConfigUpdater.reloadAll()

                    it.source.sendSuccess({ translatable(RELOAD_MESSAGE) }, true)

                    1
                }
            )
            .then(literal("save")
                .executes {
                    VSConfigUpdater.saveAll()

                    it.source.sendSuccess({ translatable(SAVE_MESSAGE) }, true)

                    1
                }
            )

        for ((typeName, configType) in CONFIG_TYPES) {
            registerGet(config, typeName, configType)
            registerEdit(config, typeName, configType)
        }

        vs.then(config)
    }

    private fun registerGet(config: LiteralArgumentBuilder<CommandSourceStack>, typeName: String, configType: ConfigType) {
        config.then(literal("get")
            .then(literal(typeName)
                .then(argument("path", StringArgumentType.greedyString())
                    .suggests { _, builder -> suggestPath(configType, builder, includeValueSuggestions = false) }
                    .executes {
                        val input = StringArgumentType.getString(it, "path")
                        val result = VSConfigUpdater.walk(configType, input)

                        if (result.entry == null) {
                            it.source.sendFailure(textLiteral("No such $typeName config entry: ${input.trim()}"))
                            return@executes 0
                        }

                        it.source.sendSuccess(
                            { textLiteral("${result.entry.dottedPath} = ${result.entry.modelEntry.getValue()}") },
                            true
                        )

                        1
                    }
                )
            )
        )
    }

    private fun registerEdit(config: LiteralArgumentBuilder<CommandSourceStack>, typeName: String, configType: ConfigType) {
        config.then(literal("edit")
            .then(literal(typeName)
                .then(argument("pathAndValue", StringArgumentType.greedyString())
                    .suggests { _, builder -> suggestPath(configType, builder, includeValueSuggestions = true) }
                    .executes {
                        val input = StringArgumentType.getString(it, "pathAndValue")
                        val result = VSConfigUpdater.walk(configType, input)

                        if (result.entry == null) {
                            it.source.sendFailure(textLiteral("No such $typeName config entry: ${input.trim()}"))
                            return@executes 0
                        }

                        val rawValue = result.remainingText.trim()
                        if (rawValue.isEmpty()) {
                            it.source.sendFailure(textLiteral("Usage: /vs config edit $typeName ${result.entry.dottedPath.replace('.', ' ')} <value>"))
                            return@executes 0
                        }

                        when (val setResult = VSConfigUpdater.setEntry(result.entry, rawValue)) {
                            is VSConfigUpdater.SetResult.InvalidValue -> {
                                it.source.sendFailure(textLiteral(setResult.reason))
                                0
                            }
                            is VSConfigUpdater.SetResult.Success -> {
                                VSConfigUpdater.saveAll()
                                VSConfigUpdater.reloadAll()

                                it.source.sendSuccess(
                                    { textLiteral("${result.entry.dottedPath}: ${setResult.oldValue} -> ${setResult.newValue}") },
                                    true
                                )

                                1
                            }
                        }
                    }
                )
            )
        )
    }
    private fun suggestPath(
        configType: ConfigType,
        builder: SuggestionsBuilder,
        includeValueSuggestions: Boolean
    ): CompletableFuture<Suggestions> {
        val typedSoFar = builder.remaining
        val result = VSConfigUpdater.walk(configType, typedSoFar)

        val consumedLength = typedSoFar.length - result.remainingText.length
        val offsetBuilder = builder.createOffset(builder.start + consumedLength)
        val partial = result.remainingText.trimStart()
        val partialOffsetBuilder = offsetBuilder.createOffset(offsetBuilder.start + (result.remainingText.length - partial.length))

        if (result.entry != null) {
            if (includeValueSuggestions) {
                for (suggestion in valueSuggestionsFor(result.entry)) {
                    if (suggestion.startsWith(partial, ignoreCase = true)) {
                        partialOffsetBuilder.suggest(suggestion)
                    }
                }
            }
            return partialOffsetBuilder.buildFuture()
        }

        for (node in result.nodesAtCursor) {
            if (node.name.startsWith(partial, ignoreCase = true)) {
                partialOffsetBuilder.suggest(node.name)
            }
        }
        return partialOffsetBuilder.buildFuture()
    }

    private fun valueSuggestionsFor(handle: VSConfigUpdater.ConfigEntryHandle): List<String> {
        return when (val current = handle.modelEntry.getValue()) {
            is Boolean -> listOf("true", "false")
            is Enum<*> -> current.declaringJavaClass.enumConstants.map { (it as Enum<*>).name }
            else -> emptyList()
        }
    }
}
