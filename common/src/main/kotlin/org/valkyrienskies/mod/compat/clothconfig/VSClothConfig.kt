package org.valkyrienskies.mod.compat.clothconfig

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import org.valkyrienskies.core.config.SidedVSConfigClass
import org.valkyrienskies.core.config.VSConfigClass
import org.valkyrienskies.core.util.serialization.VSJacksonUtil
import org.valkyrienskies.core.util.serialization.shallowCopy
import org.valkyrienskies.core.util.variance
import java.util.Optional
import java.util.function.Consumer

class VSClothConfig {

}

fun createConfigScreenFor(configClass: VSConfigClass, parent: Screen): Screen {
    return ConfigBuilder.create().apply {
        parentScreen = parent

        configClass.sides.forEach { side ->
            println(side.schemaJson)
            getOrCreateCategory(TextComponent(side.sideName)).apply {
                side.schemaJson["properties"]?.fields()?.forEach { (key, value) ->
                    if (key != "\$schema") {
                        addEntryForProperty(key, value, ::entryBuilder, side)
                    }
                }
                // side.clazz.declaredFields.forEach { field ->
                //     addEntryForField(side, field, side.inst, entryBuilder())
                // }
            }
        }

    }.build()
}

fun ConfigCategory.addEntryForProperty(
    key: String, schema: JsonNode, entryBuilder: () -> ConfigEntryBuilder, side: SidedVSConfigClass
) {
    val configJson = side.instJson
    val mapper = VSJacksonUtil.configMapper

    val description: String? = schema["description"]?.asText()
    val title: String = schema["title"]?.asText(key) ?: key
    val titleComponent = TextComponent(title)

    fun newConfigWithValue(newValue: JsonNode): ObjectNode {
        val newConfig = configJson.shallowCopy()
        newConfig.replace(key, newValue)
        return newConfig
    }

    fun validate(newValue: JsonNode): Optional<Component> {
        val errors = side.schema.validate(newConfigWithValue(newValue))
        return if (errors.isNotEmpty()) {
            Optional.of(TextComponent(errors.joinToString()))
        } else {
            Optional.empty()
        }
    }

    val defaultErrorSupplier = { value: Any -> validate(mapper.valueToTree(value)) }
    val defaultSaveConsumer =
        Consumer { value: Any -> side.attemptUpdate(newConfigWithValue(mapper.valueToTree(value))) }

    val enum: ArrayNode? = schema["enum"] as? ArrayNode

    when (schema["type"].asText()) {
        "integer" -> {
            val value = configJson[key].intValue()

            addEntry(entryBuilder().startIntField(titleComponent, value).apply {
                if (description != null) setTooltip(TextComponent(description))
                setSaveConsumer(defaultSaveConsumer.variance())
                setErrorSupplier(defaultErrorSupplier)

                schema["minimum"]?.intValue()?.let { setMin(it) }
                schema["exclusiveMinimum"]?.intValue()?.let { setMin(it + 1) }
                schema["maximum"]?.intValue()?.let { setMax(it) }
                schema["exclusiveMaximum"]?.intValue()?.let { setMax(it - 1) }
            }.build())
        }
        "number" -> {
            val value = configJson[key].doubleValue()
            addEntry(entryBuilder().startDoubleField(titleComponent, value).apply {
                if (description != null) setTooltip(TextComponent(description))
                setSaveConsumer(defaultSaveConsumer.variance())
                setErrorSupplier(defaultErrorSupplier)

                schema["minimum"]?.doubleValue()?.let { setMin(it) }
                schema["exclusiveMinimum"]?.doubleValue()?.let { setMin(it) }
                schema["maximum"]?.doubleValue()?.let { setMax(it) }
                schema["exclusiveMaximum"]?.doubleValue()?.let { setMax(it) }
            }.build())
        }
        "boolean" -> {
            val value = configJson[key].booleanValue()
            addEntry(entryBuilder().startBooleanToggle(titleComponent, value).apply {
                if (description != null) setTooltip(TextComponent(description))
                setSaveConsumer(defaultSaveConsumer.variance())
                setErrorSupplier(defaultErrorSupplier)
            }.build())
        }
        "string" -> {
            val value = configJson[key].asText()
            if (enum == null) {
                addEntry(entryBuilder().startStrField(titleComponent, value).apply {
                    if (description != null) setTooltip(TextComponent(description))
                    setSaveConsumer(defaultSaveConsumer.variance())
                    setErrorSupplier(defaultErrorSupplier)
                }.build())
            } else {
                addEntry(entryBuilder().startStringDropdownMenu(titleComponent, value).apply {
                    if (description != null) setTooltip(TextComponent(description))
                    setSaveConsumer(defaultSaveConsumer.variance())
                    setErrorSupplier(defaultErrorSupplier)

                    isSuggestionMode = false
                    setSelections(enum.mapNotNull { it.asText(null) })
                }.build())
            }
        }
        else -> {
            val value = configJson[key].toString()
            addEntry(entryBuilder().startStrField(titleComponent, value).apply {
                if (description != null) {
                    setTooltip(TextComponent(description))
                }
                setSaveConsumer { str ->
                    side.attemptUpdate(newConfigWithValue(mapper.readTree(str)))
                }
                setErrorSupplier { str ->
                    val newValue = try {
                        mapper.readTree(str)
                    } catch (ex: JsonProcessingException) {
                        return@setErrorSupplier Optional.of(TextComponent(ex.message))
                    }

                    validate(newValue)
                }
            }.build())
        }
    }
}

// fun ConfigCategory.addEntryForField(
//     side: VSConfigClass.SidedVSConfigClass, field: Field, inst: Any, builder: ConfigEntryBuilder
// ) {
//     val mapper = VSJacksonUtil.configMapper
//
//     field.isAccessible = true
//
//     if (field.name == "INSTANCE") return
//
//     val name = TextComponent(field.name)
//
//     val constraints = field.getAnnotation(JsonSchema::class.java)
//
//     val entryBuilder = when (val type = field.type) {
//         Boolean::class.java -> {
//             builder.startBooleanToggle(name, field.getBoolean(inst)).apply {
//                 setSaveConsumer { field.setBoolean(inst, it) }
//                 if (constraints == null) return@apply
//                 setTooltip(TextComponent(constraints.description))
//             }
//         }
//         Double::class.java -> {
//             builder.startDoubleField(name, field.getDouble(inst)).apply {
//                 setSaveConsumer { field.setDouble(inst, it) }
//                 if (constraints == null) return@apply
//                 setMin(constraints.min)
//                 setMax(constraints.max)
//                 setTooltip(TextComponent(constraints.description))
//             }
//         }
//         String::class.java -> {
//
//             val value = field.get(inst) as String
//             builder.startStrField(name, value).apply {
//                 setSaveConsumer { field.set(inst, it) }
//
//                 if (constraints == null) return@apply
//                 setTooltip(TextComponent(constraints.description))
//             }
//         }
//         else -> {
//             val value = mapper.writeValueAsString(field.get(inst))
//             builder.startStrField(name, value).apply {
//                 setSaveConsumer { s -> field.set(inst, mapper.readValue(s, type)) }
//                 setErrorSupplier { s ->
//                     try {
//                         mapper.readValue(s, type)
//                         Optional.empty()
//                     } catch (ex: JsonProcessingException) {
//                         Optional.of(TextComponent(ex.message))
//                     }
//                 }
//                 if (constraints == null) return@apply
//                 setTooltip(TextComponent(constraints.description))
//             }
//         }
//     }
//
//     addEntry(entryBuilder.build())
// }
