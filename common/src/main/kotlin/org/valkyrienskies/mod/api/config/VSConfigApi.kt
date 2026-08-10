package org.valkyrienskies.mod.api.config

import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.config.ModConfig
import org.valkyrienskies.core.impl.api_impl.config.VsiConfigModelImpl
import org.valkyrienskies.core.internal.config.VsiConfigModel
import org.valkyrienskies.core.internal.config.VsiConfigModelCategory
import org.valkyrienskies.core.internal.config.VsiConfigModelEntry
import org.valkyrienskies.mod.common.config.ConfigType
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ConfigUpdateEntry
import kotlin.collections.iterator

object VSConfigApi {

    @JvmStatic
    fun buildVSConfigModel(annotatedConfigObject: Any) =
        VsiConfigModelImpl.build(annotatedConfigObject)

    @JvmStatic
    fun buildForgeConfigSpec(
        configCategory: VsiConfigModelCategory,
        builder: ForgeConfigSpec.Builder,
        forgeConfigValueConsumer: (String, ForgeConfigSpec.ConfigValue<*>) -> Unit = { a, b -> }
    ): ForgeConfigSpec.Builder {
        for (node in configCategory.children) {
            val entry = node.value
            if (entry is VsiConfigModelCategory) {
                builder.push(entry.title)
                buildForgeConfigSpec(entry, builder, forgeConfigValueConsumer)
                builder.pop()
            } else if (entry is VsiConfigModelEntry<*>){
                forgeConfigValueConsumer.invoke(entry.name, defineNode(builder, entry))
            }
        }
        return builder
    }

    @JvmStatic
    fun VsiConfigModel.update(forgeConfig: ModConfig, configType: ConfigType, updatedEntries: MutableSet<ConfigUpdateEntry>) {
        root.forEachEntry { category, node ->
            val forgeKey = (category + node.name).joinToString(".")
            forgeConfig.configData.get<Any>(forgeKey)?.let { newValue ->
                val defaultValue = node.default
                if (defaultValue != null) {
                    val convertedValue = when {
                        // Handle Float stored as Double in Forge config
                        defaultValue is Float && newValue is Double -> newValue.toFloat()
                        // Handle existing enum value stored as String
                        defaultValue is Enum<*> -> {
                            when (newValue) {
                                is String -> {
                                    // Convert string name to enum instance
                                    @Suppress("UNCHECKED_CAST")
                                    val enumConstants = defaultValue.declaringJavaClass.enumConstants as Array<Enum<*>>
                                    enumConstants.find { it.name == newValue }
                                }
                                is Enum<*> -> newValue
                                else -> null
                            }
                        }
                        defaultValue::class.isInstance(newValue) -> newValue
                        else -> null
                    }

                    if (convertedValue != node.getValue()) {
                        updatedEntries.add(ConfigUpdateEntry(configType, category, node.name))
                    }

                    if (convertedValue != null) {
                        @Suppress("UNCHECKED_CAST")
                        (node as VsiConfigModelEntry<Any>).setValue(convertedValue)
                    }
                }
            }
        }
    }

    private fun VsiConfigModelCategory.forEachEntry(category: List<String> = emptyList(), callback: (List<String>, VsiConfigModelEntry<*>) -> Unit) {
        for (node in children) {
            val value = node.value
            if (value is VsiConfigModelEntry<*>) {
                callback(category, value)
            } else if (value is VsiConfigModelCategory) {
                value.forEachEntry(category + value.title, callback)
            }
        }
    }

    private fun defineNode(
        builder: ForgeConfigSpec.Builder,
        entry: VsiConfigModelEntry<*>
    ): ForgeConfigSpec.ConfigValue<*> {
        fun <T> define(v: T) = builder.define<T>(entry.name, v)

        @Suppress("UNCHECKED_CAST")
        fun <T : Enum<T>> defineEnum(builder: ForgeConfigSpec.Builder, value: Enum<*>): ForgeConfigSpec.EnumValue<*> {
            return builder.defineEnum(entry.name, value as T)
        }

        fun <T : Comparable<T>> defineNumeric(v: T) =
            if (entry.min == null || entry.max == null) {
                define(if (v is Float) v.toDouble() else v)
            } else {
                when (v) {
                    is Int -> builder.defineInRange(entry.name, v, entry.min as Int, entry.max as Int)
                    is Long -> builder.defineInRange(entry.name, v, entry.min as Long, entry.max as Long)
                    is Float -> builder.defineInRange(entry.name, v.toDouble(), (entry.min as Float).toDouble(), (entry.max as Float).toDouble())
                    is Double -> builder.defineInRange(entry.name, v, entry.min as Double, entry.max as Double)
                    else -> throw IllegalArgumentException("Non numeric type $v not accepted")
                }
            }


        entry.description?.let(builder::comment)

        return when (val v = entry.getValue()) {
            is Int -> defineNumeric(v)
            is Long -> defineNumeric(v)
            is Float -> defineNumeric(v)
            is Double -> defineNumeric(v)
            is Boolean -> define(v)
            is String -> define(v)
            is Enum<*> -> defineEnum(builder, v)
            else -> {
                throw IllegalArgumentException("invalid config type $v of class ${v?.javaClass}")
            }
        }
    }
}
