package org.valkyrienskies.mod.common.config

import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.config.ModConfig
import org.jetbrains.annotations.ApiStatus
import org.valkyrienskies.core.impl.api_impl.config.VsiConfigModelImpl
import org.valkyrienskies.core.internal.config.VsiConfigModel
import org.valkyrienskies.core.internal.config.VsiConfigModelCategory
import org.valkyrienskies.core.internal.config.VsiConfigModelEntry
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ConfigUpdateEntry
import kotlin.collections.iterator

object VSConfigUpdater {

    @JvmStatic
    val forgeConfigValuesMap: HashMap<String, ForgeConfigSpec.ConfigValue<*>> = HashMap()

    private val core_server_config = ValkyrienSkiesMod.vsCore.getServerConfig()
    val CORE_SERVER_SPEC: ForgeConfigSpec = buildConfigSpec(core_server_config.root, ForgeConfigSpec.Builder()).build()

    private val server_config = VsiConfigModelImpl.build(VSGameConfig.SERVER)
    val SERVER_SPEC: ForgeConfigSpec = buildConfigSpec(server_config.root, ForgeConfigSpec.Builder()).build()

    private val common_config = VsiConfigModelImpl.build(VSGameConfig.COMMON)
    val COMMON_SPEC: ForgeConfigSpec = buildConfigSpec(common_config.root, ForgeConfigSpec.Builder()).build()

    private val client_config = VsiConfigModelImpl.build(VSGameConfig.CLIENT)
    val CLIENT_SPEC: ForgeConfigSpec = buildConfigSpec(client_config.root, ForgeConfigSpec.Builder()).build()

    /**
     * Call this from platform events when config is loaded or updated
     **/
    @ApiStatus.Internal
    fun update(config: ModConfig) {
        val updatedEntries = mutableSetOf<ConfigUpdateEntry>()

        core_server_config.update(config, ConfigType.CORE_SERVER, updatedEntries)
        server_config.update(config, ConfigType.SERVER, updatedEntries)
        common_config.update(config, ConfigType.COMMON, updatedEntries)
        client_config.update(config, ConfigType.CLIENT, updatedEntries)

        if (updatedEntries.isNotEmpty()) {
            VSGameEvents.configUpdated.emit(updatedEntries)
        }
    }

     fun buildConfigSpec(configCategory: VsiConfigModelCategory, builder: ForgeConfigSpec.Builder): ForgeConfigSpec.Builder {
        for (node in configCategory.children) {
            val entry = node.value
            if (entry is VsiConfigModelCategory) {
                builder.push(entry.title)
                buildConfigSpec(entry, builder)
                builder.pop()
            } else if (entry is VsiConfigModelEntry<*>){
                val configValue = defineNode(builder, entry)
                forgeConfigValuesMap[entry.name] = configValue
            }
        }
        return builder
    }

    private fun defineNode(builder: ForgeConfigSpec.Builder, entry: VsiConfigModelEntry<*>): ForgeConfigSpec.ConfigValue<*> {
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

    private fun VsiConfigModel.update(forgeConfig: ModConfig, configType: ConfigType, updatedEntries: MutableSet<ConfigUpdateEntry>) {
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
}
