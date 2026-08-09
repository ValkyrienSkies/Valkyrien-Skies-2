package org.valkyrienskies.mod.common.config

import com.electronwill.nightconfig.core.file.FileConfig
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.config.ModConfig
import org.jetbrains.annotations.ApiStatus
import org.valkyrienskies.core.internal.config.VsiConfigModelCategory
import org.valkyrienskies.core.internal.config.VsiConfigModelEntry
import org.valkyrienskies.mod.api.config.VSConfigApi
import org.valkyrienskies.mod.api.config.VSConfigApi.buildForgeConfigSpec
import org.valkyrienskies.mod.api.config.VSConfigApi.update
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ConfigUpdateEntry
import org.valkyrienskies.mod.util.logger

object VSConfigUpdater {

    private val LOGGER by logger()

    @JvmStatic
    val forgeConfigValuesMap: HashMap<String, ForgeConfigSpec.ConfigValue<*>> = HashMap()

    private val registeredConfigs: HashMap<String, ModConfig> = HashMap()

    class ConfigEntryHandle(
        val configType: ConfigType,
        val path: List<String>,
        val modelEntry: VsiConfigModelEntry<*>,
        val forgeValue: ForgeConfigSpec.ConfigValue<*>
    ) {
        val dottedPath: String get() = path.joinToString(".")
    }

    private val entryHandles: MutableMap<ConfigType, LinkedHashMap<String, ConfigEntryHandle>> =
        ConfigType.values().associateWithTo(HashMap()) { LinkedHashMap() }

    private fun pathAwareConsumerFor(configType: ConfigType) =
        { path: List<String>, entry: VsiConfigModelEntry<*>, forgeValue: ForgeConfigSpec.ConfigValue<*> ->
            val handle = ConfigEntryHandle(configType, path, entry, forgeValue)
            entryHandles.getValue(configType)[handle.dottedPath.lowercase()] = handle
        }

    private val configValueConsumer = { name: String, value: ForgeConfigSpec.ConfigValue<*> ->
        forgeConfigValuesMap[name] = value
    }

    private val core_server_config = ValkyrienSkiesMod.vsCore.getServerConfig()
    val CORE_SERVER_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = core_server_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.CORE_SERVER)
    ).build()

    private val server_config = VSConfigApi.buildVSConfigModel(VSGameConfig.SERVER)
    val SERVER_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = server_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.SERVER)
    ).build()

    private val common_config = VSConfigApi.buildVSConfigModel(VSGameConfig.COMMON)
    val COMMON_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = common_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.COMMON)
    ).build()

    private val client_config = VSConfigApi.buildVSConfigModel(VSGameConfig.CLIENT)
    val CLIENT_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = client_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.CLIENT)
    ).build()

    /**
     * Call this from platform events when config is loaded or updated
     **/
    @ApiStatus.Internal
    fun update(config: ModConfig) {
        registeredConfigs[config.fileName] = config

        val updatedEntries = mutableSetOf<ConfigUpdateEntry>()

        core_server_config.update(config, ConfigType.CORE_SERVER, updatedEntries)
        server_config.update(config, ConfigType.SERVER, updatedEntries)
        common_config.update(config, ConfigType.COMMON, updatedEntries)
        client_config.update(config, ConfigType.CLIENT, updatedEntries)

        if (updatedEntries.isNotEmpty()) {
            VSGameEvents.configUpdated.emit(updatedEntries)
        }
    }


    @JvmStatic
    fun reloadAll() {
        for (config in registeredConfigs.values) {
            try {
                (config.configData as? FileConfig)?.load()
            } catch (e: Exception) {
                LOGGER.warn("Failed to reload config file for ${config.fileName}", e)
            }
            update(config)
        }
    }

    @JvmStatic
    fun saveAll() {
        for (config in registeredConfigs.values) {
            try {
                config.save()
            } catch (e: Exception) {
                LOGGER.warn("Failed to save config file for ${config.fileName}", e)
            }
        }
    }

    @JvmStatic
    fun syncBeforeShutdown() {
        reloadAll()
        saveAll()
    }

    @JvmStatic
    fun findEntry(configType: ConfigType, path: List<String>): ConfigEntryHandle? =
        entryHandles[configType]?.get(path.joinToString(".").lowercase())

    @JvmStatic
    fun pathsFor(configType: ConfigType): List<String> =
        entryHandles[configType]?.values?.map { it.dottedPath } ?: emptyList()

    sealed class ConfigNode {
        abstract val name: String
        class Category(override val name: String, val children: List<ConfigNode>) : ConfigNode()
        class Entry(override val name: String, val handle: ConfigEntryHandle) : ConfigNode()
    }

    private val configTrees: Map<ConfigType, List<ConfigNode>> by lazy {
        mapOf(
            ConfigType.CORE_SERVER to buildTree(core_server_config.root, ConfigType.CORE_SERVER, emptyList()),
            ConfigType.SERVER to buildTree(server_config.root, ConfigType.SERVER, emptyList()),
            ConfigType.COMMON to buildTree(common_config.root, ConfigType.COMMON, emptyList()),
            ConfigType.CLIENT to buildTree(client_config.root, ConfigType.CLIENT, emptyList())
        )
    }

    private const val GENERAL_CATEGORY_NAME = "General"

    private fun buildTree(
        category: VsiConfigModelCategory,
        configType: ConfigType,
        path: List<String>
    ): List<ConfigNode> {
        val categories = mutableListOf<ConfigNode.Category>()
        val looseEntries = mutableListOf<ConfigNode.Entry>()

        for (node in category.children) {
            val value = node.value
            if (value is VsiConfigModelCategory) {
                categories.add(ConfigNode.Category(value.title, buildTree(value, configType, path + value.title)))
            } else if (value is VsiConfigModelEntry<*>) {
                val handle = entryHandles[configType]?.get((path + value.name).joinToString(".").lowercase())
                if (handle != null) {
                    looseEntries.add(ConfigNode.Entry(value.name, handle))
                }
            }
        }

        return if (categories.isNotEmpty() && looseEntries.isNotEmpty()) {
            categories + ConfigNode.Category(GENERAL_CATEGORY_NAME, looseEntries)
        } else {
            categories + looseEntries
        }
    }

    @JvmStatic
    fun rootNodes(configType: ConfigType): List<ConfigNode> = configTrees[configType] ?: emptyList()

    class WalkResult(
        val entry: ConfigEntryHandle?,
        val nodesAtCursor: List<ConfigNode>,

        val remainingText: String
    )


    @JvmStatic
    fun walk(configType: ConfigType, input: String): WalkResult {
        var nodes = rootNodes(configType)
        var text = input
        while (true) {
            val match = nodes
                .filter { node ->
                    text.startsWith(node.name, ignoreCase = true) &&
                        (text.length == node.name.length || text[node.name.length].isWhitespace())
                }
                .maxByOrNull { it.name.length }
                ?: return WalkResult(null, nodes, text)

            var consume = match.name.length
            while (consume < text.length && text[consume].isWhitespace()) consume++
            text = text.substring(consume)

            when (match) {
                is ConfigNode.Category -> nodes = match.children
                is ConfigNode.Entry -> return WalkResult(match.handle, emptyList(), text)
            }
        }
    }

    sealed class SetResult {
        data class Success(val handle: ConfigEntryHandle, val oldValue: Any?, val newValue: Any) : SetResult()
        data class InvalidValue(val reason: String) : SetResult()
    }


    @JvmStatic
    fun setEntry(handle: ConfigEntryHandle, rawValue: String): SetResult {
        val current = handle.modelEntry.getValue()
        val parsed = coerce(current, rawValue)
            ?: return SetResult.InvalidValue(describeExpectedType(handle, current))

        val min = handle.modelEntry.min
        val max = handle.modelEntry.max
        if (min != null && max != null && parsed is Comparable<*>) {
            @Suppress("UNCHECKED_CAST")
            val comparable = parsed as Comparable<Any>
            if (comparable < (min as Any) || comparable > (max as Any)) {
                return SetResult.InvalidValue("Value must be between $min and $max")
            }
        }

        @Suppress("UNCHECKED_CAST")
        (handle.modelEntry as VsiConfigModelEntry<Any>).setValue(parsed)

        val forgeParsed = if (parsed is Float) parsed.toDouble() else parsed
        @Suppress("UNCHECKED_CAST")
        (handle.forgeValue as ForgeConfigSpec.ConfigValue<Any>).set(forgeParsed)

        return SetResult.Success(handle, current, parsed)
    }

    private fun coerce(current: Any?, raw: String): Any? = when (current) {
        is Int -> raw.toIntOrNull()
        is Long -> raw.toLongOrNull()
        is Float -> raw.toFloatOrNull()
        is Double -> raw.toDoubleOrNull()
        is Boolean -> when (raw.lowercase()) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> null
        }
        is String -> raw
        is Enum<*> -> {
            @Suppress("UNCHECKED_CAST")
            val constants = current.declaringJavaClass.enumConstants as Array<Enum<*>>
            constants.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
        else -> null
    }

    private fun describeExpectedType(handle: ConfigEntryHandle, current: Any?): String = when (current) {
        is Int, is Long -> "Value must be a whole number"
        is Float, is Double -> "Value must be a number"
        is Boolean -> "Value must be true or false"
        is Enum<*> -> {
            @Suppress("UNCHECKED_CAST")
            val constants = current.declaringJavaClass.enumConstants as Array<Enum<*>>
            "Value must be one of: ${constants.joinToString(", ") { it.name }}"
        }
        else -> "Invalid value"
    }
}
