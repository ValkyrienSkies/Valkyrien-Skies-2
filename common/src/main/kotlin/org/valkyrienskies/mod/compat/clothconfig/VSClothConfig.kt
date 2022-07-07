package org.valkyrienskies.mod.compat.clothconfig

import com.fasterxml.jackson.core.JsonProcessingException
import com.github.imifou.jsonschema.module.addon.annotation.JsonSchema
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.TextComponent
import org.valkyrienskies.core.config.VSConfigClass
import org.valkyrienskies.core.util.serialization.VSJacksonUtil
import java.lang.reflect.Field
import java.util.Optional

class VSClothConfig {

}

fun createConfigScreenFor(configClass: VSConfigClass, parent: Screen): Screen {
    return ConfigBuilder.create().apply {
        parentScreen = parent

        configClass.sides.forEach { side ->
            getOrCreateCategory(TextComponent(side.sideName)).apply {
                side.clazz.declaredFields.forEach { field ->
                    addEntryForField(field, side.inst, entryBuilder())
                }
            }
        }

    }.build()
}

fun ConfigCategory.addEntryForField(field: Field, inst: Any, builder: ConfigEntryBuilder) {
    val mapper = VSJacksonUtil.configMapper

    field.isAccessible = true

    if (field.name == "INSTANCE") return

    val name = TextComponent(field.name)

    val constraints = field.getAnnotation(JsonSchema::class.java)

    val entryBuilder = when (val type = field.type) {
        Boolean::class.java -> {
            builder.startBooleanToggle(name, field.getBoolean(inst))
                .setSaveConsumer { b -> field.setBoolean(inst, b) }
        }
        Double::class.java -> {
            builder.startDoubleField(name, field.getDouble(inst)).apply {
                if (constraints == null) return@apply
                setMin(constraints.min)
                setMax(constraints.max)
            }
        }
        else -> {
            val value = mapper.writeValueAsString(field.get(inst))
            builder.startStrField(name, value)
                .setErrorSupplier { s ->
                    try {
                        mapper.readValue(s, type)
                        Optional.empty()
                    } catch (ex: JsonProcessingException) {
                        Optional.of(TextComponent(ex.message))
                    }
                }
                .setSaveConsumer { s -> field.set(inst, mapper.readValue(s, type)) }
        }
    }

    addEntry(entryBuilder.build())
}
