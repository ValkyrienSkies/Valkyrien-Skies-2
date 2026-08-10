package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component.translatable
import net.minecraftforge.common.ForgeConfigSpec
import org.valkyrienskies.mod.common.config.ConfigType
import org.valkyrienskies.mod.common.config.VSConfigUpdater
import org.valkyrienskies.mod.common.config.VSGameConfig
import java.util.concurrent.CompletableFuture

object ConfigCommand {

    const val CONFIG_PROP_NOT_FOUND = "command.valkyrienskies.config.not_found"
    const val CONFIG_PROP_RESULT = "command.valkyrienskies.config.result"
    const val CONFIG_PROP_SET = "command.valkyrienskies.config.set"

    private val CONFIG_TYPES = linkedMapOf(
        "core" to ConfigType.CORE_SERVER,
        "server" to ConfigType.SERVER,
        "common" to ConfigType.COMMON,
        "client" to ConfigType.CLIENT
    )

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        val config = literal("config")
            .requires { it.hasPermission(VSGameConfig.SERVER.Commands.configCommandPerms) }

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
                        val resultEntry = VSConfigUpdater.forgeConfigValuesMap.get(input)

                        if (resultEntry == null) {
                            it.source.sendFailure(
                                //"No such $typeName config entry: $input.trim()"
                                translatable(CONFIG_PROP_NOT_FOUND, typeName, input.trim())
                            )
                            return@executes 0
                        }

                        val result = resultEntry.get()

                        it.source.sendSuccess(
                            {
                                //"$input is set to $result"
                                translatable(CONFIG_PROP_RESULT, input, result.toString())
                            },
                            true
                        )

                        if (result is Number) {
                            return@executes result.toInt()
                        }

                        if (result is Enum<*>) {
                            return@executes result.ordinal
                        }

                        if (result is Boolean) {
                            return@executes result.compareTo(false)
                        }

                        return@executes 1
                    }
                )
            )
        )
    }

    private fun registerEdit(config: LiteralArgumentBuilder<CommandSourceStack>, typeName: String, configType: ConfigType) {
        config.then(literal("edit")
            .then(literal(typeName)
                .then(argument("path", StringArgumentType.string())
                    .suggests { _, builder -> suggestPath(configType, builder, includeValueSuggestions = true) }
                    .then(argument("value", StringArgumentType.string())
                        .executes {
                            val input = StringArgumentType.getString(it, "path")
                            val resultEntry = VSConfigUpdater.forgeConfigValuesMap.get(input)

                            if (resultEntry == null) {
                                it.source.sendFailure(
                                    //"No such $typeName config entry: $input.trim()"
                                    translatable(CONFIG_PROP_NOT_FOUND, typeName, input.trim())
                                )
                                return@executes 0
                            }

                            val valueString = StringArgumentType.getString(it, "value")
                            val desiredType = resultEntry.get()

                            val value = stringToType(valueString, desiredType)

                            (resultEntry as ForgeConfigSpec.ConfigValue<Any>).set(value)


                            it.source.sendSuccess(
                                {
                                    //"Set $input value: $old -> $new"
                                    translatable(CONFIG_PROP_SET, input, desiredType, value)
                                },
                                true
                            )

                            1
                        }
                    )

                )
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stringToType(
        valueString: String,
        desiredTypeInstance: T
    ): T {
        if (desiredTypeInstance is String) {
            return valueString as T
        }

        if (desiredTypeInstance is Boolean) {
            if (valueString == "0") return false as T
            if (valueString == "1") return true as T
            if (valueString.lowercase() == "false") return false as T
            if (valueString.lowercase() == "true") return true as T
            return false as T
        }

        if (desiredTypeInstance is Enum<*>) {
            try {
                return java.lang.Enum.valueOf(desiredTypeInstance.javaClass as Class<out Enum<*>>, valueString) as T
            } catch (e: IllegalArgumentException) {
                // The string wasn't a valid enum value
            }
        }

        if (desiredTypeInstance is Number) {
            return valueString.toDouble() as T
        }

        return desiredTypeInstance
    }

    private fun suggestPath(
        configType: ConfigType,
        builder: SuggestionsBuilder,
        includeValueSuggestions: Boolean
    ): CompletableFuture<Suggestions> {
        val typedSoFar = builder.remaining

        val matchingKeys = VSConfigUpdater.forgeConfigValuesMap.filterKeys { it.startsWith(typedSoFar) }.keys.toList()
        matchingKeys.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    /*private fun valueSuggestionsFor(handle: VSConfigUpdater.ConfigEntryHandle): List<String> {
        return when (val current = handle.modelEntry.getValue()) {
            is Boolean -> listOf("true", "false")
            is Enum<*> -> current.declaringJavaClass.enumConstants.map { (it as Enum<*>).name }
            else -> emptyList()
        }
    }*/
}
