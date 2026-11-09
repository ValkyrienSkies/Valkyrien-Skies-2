package org.valkyrienskies.mod.api.config

import net.neoforged.neoforge.common.ModConfigSpec
import org.valkyrienskies.core.impl.api_impl.config.VsiConfigModelImpl
import org.valkyrienskies.core.internal.config.VsiConfigModel
import org.valkyrienskies.core.internal.config.VsiConfigModelCategory
import org.valkyrienskies.core.internal.config.VsiConfigModelEntry
import org.valkyrienskies.mod.common.config.ConfigType
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ConfigUpdateEntry

object VSConfigApi {

    @JvmStatic
    fun buildVSConfigModel(annotatedConfigObject: Any) =
        VsiConfigModelImpl.build(annotatedConfigObject)

    @JvmStatic
    fun buildModConfigSpec(
        configCategory: VsiConfigModelCategory,
        builder: ModConfigSpec.Builder,
        forgeConfigValueConsumer: (String, ModConfigSpec.ConfigValue<*>) -> Unit = { a, b -> },
        path: List<String> = emptyList(),
        pathAwareConsumer: (List<String>, VsiConfigModelEntry<*>, ModConfigSpec.ConfigValue<*>) -> Unit = { _, _, _ -> }
    ): ModConfigSpec.Builder {
        for (node in configCategory.children) {
            val entry = node.value
            if (entry is VsiConfigModelCategory) {
                // As with leaf entries, feed the section title directly as the translation
                // key so the raw-key fallback renders the user-friendly category name
                // ("Block Tint") instead of NeoForge's default derived key
                // ("valkyrienskies.configuration.Block Tint").
                builder.translation(entry.title)
                builder.push(entry.title)
                buildModConfigSpec(entry, builder, forgeConfigValueConsumer, path + entry.title, pathAwareConsumer)
                builder.pop()
            } else if (entry is VsiConfigModelEntry<*>){
                val value = defineNode(builder, entry)
                forgeConfigValueConsumer.invoke(entry.name, value)
                pathAwareConsumer.invoke(path + entry.name, entry, value)
            }
        }
        return builder
    }

    /**
     * Walks the VsiConfigModel and pulls each entry's current value from [getByKey] (which
     * is typically backed by a NightConfig CommentedConfig on the platform side), converting
     * types as needed and writing through to each entry's [VsiConfigModelEntry.setValue].
     * This is what propagates user edits from the TOML file (and the NeoForge config screen)
     * back into the in-memory Kotlin vars like `VSGameConfig.CLIENT.renderDebugText`.
     */
    fun VsiConfigModel.applyToModel(
        getByKey: (String) -> Any?,
        configType: ConfigType,
        updatedEntries: MutableSet<ConfigUpdateEntry>
    ) {
        root.forEachEntry { category, node ->
            val key = (category + node.name).joinToString(".")
            val newValue = getByKey(key) ?: return@forEachEntry
            val defaultValue = node.default ?: return@forEachEntry
            val convertedValue = when {
                // Forge/NightConfig stores Floats as Doubles; coerce on read-back.
                defaultValue is Float && newValue is Double -> newValue.toFloat()
                // Enums round-trip through the TOML as their name string.
                defaultValue is Enum<*> -> when (newValue) {
                    is String -> {
                        @Suppress("UNCHECKED_CAST")
                        val constants = defaultValue.declaringJavaClass.enumConstants as Array<Enum<*>>
                        constants.find { it.name.equals(newValue, ignoreCase = true) }
                    }
                    is Enum<*> -> newValue
                    else -> null
                }
                defaultValue::class.isInstance(newValue) -> newValue
                else -> null
            } ?: return@forEachEntry

            if (convertedValue != node.getValue()) {
                updatedEntries.add(ConfigUpdateEntry(configType, category, node.name))
            }
            @Suppress("UNCHECKED_CAST")
            (node as VsiConfigModelEntry<Any>).setValue(convertedValue)
        }
    }

    private fun VsiConfigModelCategory.forEachEntry(
        category: List<String> = emptyList(),
        callback: (List<String>, VsiConfigModelEntry<*>) -> Unit
    ) {
        for (node in children) {
            val value = node.value
            if (value is VsiConfigModelEntry<*>) {
                callback(category, value)
            } else if (value is VsiConfigModelCategory) {
                value.forEachEntry(category + value.title, callback)
            }
        }
    }

    private fun prettifyCamelCase(name: String): String {
        if (name.isEmpty()) return name
        val out = StringBuilder()
        out.append(name[0].uppercaseChar())
        for (i in 1 until name.length) {
            val c = name[i]
            if (c.isUpperCase() && !name[i - 1].isUpperCase()) out.append(' ')
            out.append(c)
        }
        return out.toString()
    }

    private fun defineNode(
        builder: ModConfigSpec.Builder,
        entry: VsiConfigModelEntry<*>
    ): ModConfigSpec.ConfigValue<*> {
        fun <T> define(v: T) = builder.define<T>(entry.name, v)

        @Suppress("UNCHECKED_CAST")
        fun <T : Enum<T>> defineEnum(builder: ModConfigSpec.Builder, value: Enum<*>): ModConfigSpec.EnumValue<*> {
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
        // NeoForge's ConfigurationScreen renders the translation key verbatim when no
        // lang entry is found. We don't ship a lang mapping for every config entry, so
        // instead of leaving it as the default "valkyrienskies.configuration.fooBarBaz"
        // (which MC prints raw), feed a prettified form of the Java name. The raw-key
        // fallback then reads as "Foo Bar Baz". If a real translation is ever added
        // later, it can key off this same string.
        builder.translation(prettifyCamelCase(entry.name))

        return when (val v = entry.getValue()) {
            is Int -> defineNumeric(v)
            is Long -> defineNumeric(v)
            is Float -> defineNumeric(v)
            is Double -> defineNumeric(v)
            // Booleans must go through the specific define(String, boolean) overload so we
            // get back a BooleanValue — the generic define<T>(String, T) returns a plain
            // ConfigValue<Boolean> that NeoForge's UI refuses to edit ("this value cannot
            // be edited by the UI"). Kotlin's dispatch picks the generic overload when the
            // argument is a boxed Boolean, so force the unboxed primitive here.
            is Boolean -> builder.define(entry.name, v)
            is String -> define(v)
            is Enum<*> -> defineEnum(builder, v)
            else -> {
                throw IllegalArgumentException("invalid config type $v of class ${v?.javaClass}")
            }
        }
    }
}
