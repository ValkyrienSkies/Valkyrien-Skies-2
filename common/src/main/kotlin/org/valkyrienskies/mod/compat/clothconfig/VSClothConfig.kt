package org.valkyrienskies.mod.compat.clothconfig

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.ValidationMessage
import com.rubydesic.jacksonktdsl.obj
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.ChatFormatting.GRAY
import net.minecraft.ChatFormatting.ITALIC
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import org.valkyrienskies.core.config.SidedVSConfigClass
import org.valkyrienskies.core.config.VSConfigClass
import org.valkyrienskies.core.util.serialization.VSJacksonUtil
import org.valkyrienskies.core.util.splitCamelCaseAndCapitalize
import java.util.Optional

object VSClothConfig {

    @JvmStatic
    fun createConfigScreenFor(parent: Screen, vararg configClasses: VSConfigClass): Screen {
        return ConfigBuilder.create().apply {
            parentScreen = parent

            configClasses.forEach { configClass ->
                configClass.sides.forEach { side ->
                    val name = if (configClasses.size == 1) side.sideName else "${configClass.name} - ${side.sideName}"
                    addEntriesForConfig(getOrCreateCategory(TextComponent(name)), ::entryBuilder, side)
                }
            }
            savingRunnable = Runnable {
                configClasses.forEach { configClass ->
                    configClass.writeToDisk()
                    configClass.syncToServer()
                }
            }
        }.build()
    }

    private fun addEntriesForConfig(
        category: ConfigCategory,
        entryBuilder: () -> ConfigEntryBuilder,
        side: SidedVSConfigClass
    ) {
        val configJson = side.generateInstJson()
        side.schemaJson["properties"]?.fields()?.forEach { (key, schema) ->
            if (key != "\$schema") {
                getEntriesForProperty(
                    key,
                    configJson[key], schema, entryBuilder,
                    save = { newValueToMerge ->
                        side.attemptUpdate(
                            side.generateInstJsonAndMergeWith(key, newValueToMerge)
                        )
                    },
                    validate = { newValueToMerge ->
                        side.schema.validate(
                            side.generateInstJsonAndMergeWith(key, newValueToMerge)
                        )
                    }
                ).forEach(category::addEntry)
            }
        }
    }

    private fun getEntriesForProperty(
        key: String,
        currentValue: JsonNode,
        schema: JsonNode,
        entryBuilder: () -> ConfigEntryBuilder,
        save: (JsonNode) -> Unit,
        validate: (JsonNode) -> Set<ValidationMessage>
    ): List<AbstractConfigListEntry<*>> {
        val mapper = VSJacksonUtil.configMapper
        val entries = mutableListOf<AbstractConfigListEntry<*>>()

        val description: String? = schema["description"]?.asText()
        val title: String = schema["title"]?.asText(null) ?: key.splitCamelCaseAndCapitalize()

        val titleComponent = TextComponent(title)

        fun getValidationMessageComponent(value: JsonNode): Optional<Component> {
            val errors = validate(value)
            return if (errors.isNotEmpty()) {
                Optional.of(TextComponent(errors.joinToString()))
            } else {
                Optional.empty()
            }
        }

        fun <T> defaultError(value: T): Optional<Component> {
            return getValidationMessageComponent(mapper.valueToTree(value))
        }

        fun <T> defaultSave(value: T) {
            save(mapper.valueToTree(value))
        }

        val enum: ArrayNode? = schema["enum"] as? ArrayNode

        val type = schema["type"].asText()
        val tooltip: TextComponent? = null

        when {
            type == "integer" -> {
                val value = currentValue.intValue()

                entries.add(
                    entryBuilder().startIntField(titleComponent, value).apply {
                        if (tooltip != null) setTooltip(tooltip)
                        setSaveConsumer(::defaultSave)
                        setErrorSupplier(::defaultError)

                        schema["minimum"]?.intValue()?.let { setMin(it) }
                        schema["exclusiveMinimum"]?.intValue()?.let { setMin(it + 1) }
                        schema["maximum"]?.intValue()?.let { setMax(it) }
                        schema["exclusiveMaximum"]?.intValue()?.let { setMax(it - 1) }
                    }.build()
                )
            }

            type == "number" -> {
                val value = currentValue.doubleValue()
                entries.add(
                    entryBuilder().startDoubleField(titleComponent, value).apply {
                        if (tooltip != null) setTooltip(tooltip)
                        setSaveConsumer(::defaultSave)
                        setErrorSupplier(::defaultError)

                        schema["minimum"]?.doubleValue()?.let { setMin(it) }
                        schema["exclusiveMinimum"]?.doubleValue()?.let { setMin(it) }
                        schema["maximum"]?.doubleValue()?.let { setMax(it) }
                        schema["exclusiveMaximum"]?.doubleValue()?.let { setMax(it) }
                    }.build()
                )
            }

            type == "boolean" -> {
                val value = currentValue.booleanValue()
                entries.add(
                    entryBuilder().startBooleanToggle(titleComponent, value).apply {
                        if (tooltip != null) setTooltip(tooltip)
                        setSaveConsumer(::defaultSave)
                        setErrorSupplier(::defaultError)
                    }.build()
                )
            }

            type == "string" -> {
                val value = currentValue.asText()
                if (enum == null) {
                    entries.add(
                        entryBuilder().startStrField(titleComponent, value).apply {
                            if (tooltip != null) setTooltip(tooltip)
                            setSaveConsumer(::defaultSave)
                            setErrorSupplier(::defaultError)
                        }.build()
                    )
                } else {
                    entries.add(
                        entryBuilder().startStringDropdownMenu(titleComponent, value).apply {
                            if (tooltip != null) setTooltip(tooltip)
                            setSaveConsumer(::defaultSave)
                            setErrorSupplier(::defaultError)

                            isSuggestionMode = false
                            setSelections(enum.mapNotNull { it.asText(null) })
                        }.build()
                    )
                }
            }

            type == "object" -> {
                currentValue as ObjectNode
                val properties = schema["properties"] as ObjectNode
                val subEntries = properties.fields().asSequence().flatMap { (subKey, schema) ->
                    getEntriesForProperty(
                        subKey, currentValue[subKey], schema, entryBuilder,
                        save = { newValue -> save(obj { subKey to newValue }) },
                        validate = { newValue -> validate(obj { subKey to newValue }) }
                    )
                }.toList()
                entries.add(
                    entryBuilder().startSubCategory(titleComponent, subEntries).build()
                )
            }

            type == "array" && schema["items"]["type"].asText() == "string" -> {
                val arr = currentValue as ArrayNode
                val textArr = arr.mapNotNull { it.asText(null) }
                entries.add(
                    entryBuilder().startStrList(titleComponent, textArr).apply {
                        if (tooltip != null) setTooltip(tooltip)
                        setSaveConsumer(::defaultSave)
                        setErrorSupplier(::defaultError)
                    }.build()
                )
            }

            type == "array" && schema["items"]["type"].asText() == "integer" -> {
                val arr = currentValue as ArrayNode
                val intArr = arr.mapNotNull { it.asInt() }
                entries.add(
                    entryBuilder().startIntList(titleComponent, intArr).apply {
                        if (tooltip != null) setTooltip(tooltip)
                        setSaveConsumer(::defaultSave)
                        setErrorSupplier(::defaultError)
                    }.build()
                )
            }

            type == "array" && schema["items"]["type"].asText() == "number" -> {
                val arr = currentValue as ArrayNode
                val doubleArr = arr.mapNotNull { it.asDouble() }
                entries.add(
                    entryBuilder().startDoubleList(titleComponent, doubleArr).apply {
                        if (tooltip != null) setTooltip(tooltip)
                        setSaveConsumer(::defaultSave)
                        setErrorSupplier(::defaultError)
                    }.build()
                )
            }

            else -> {
                val value = currentValue.toString()
                entries.add(
                    entryBuilder().startStrField(titleComponent, value).apply {
                        if (tooltip != null) setTooltip(tooltip)
                        setSaveConsumer { str -> defaultSave(mapper.readTree(str)) }
                        setErrorSupplier { str ->
                            val newValue = try {
                                mapper.readTree(str)
                            } catch (ex: JsonProcessingException) {
                                return@setErrorSupplier Optional.of(TextComponent(ex.message))
                            }

                            getValidationMessageComponent(newValue)
                        }
                    }.build()
                )
            }
        }

        if (description != null) {
            entries.add(entryBuilder().startTextDescription(TextComponent(description).withStyle(GRAY, ITALIC)).build())
        }

        return entries
    }
}
