package org.valkyrienskies.mod.common.config

import net.neoforged.neoforge.common.ModConfigSpec
import org.valkyrienskies.core.internal.config.VsiConfigModelEntry
import org.valkyrienskies.mod.api.config.VSConfigApi
import org.valkyrienskies.mod.api.config.VSConfigApi.applyToModel
import org.valkyrienskies.mod.api.config.VSConfigApi.buildModConfigSpec
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ConfigUpdateEntry

/**
 * Holds the four ModConfigSpecs (core-server, server, common, client) and the VsiConfigModels
 * that back them. The specs are registered with NeoForge by the platform module
 * (ValkyrienSkiesModForge), which also forwards ModConfigEvent notifications here via
 * [applyFromConfigLoad] so that TOML changes propagate back into the in-memory Kotlin
 * configuration objects (e.g. `VSGameConfig.CLIENT.renderDebugText`).
 *
 * The `net.neoforged.fml.config.ModConfig` type is intentionally not referenced from this
 * module — it's not on the common module's compile classpath. Callers pass an abstracted
 * `(String) -> Any?` key-lookup so the platform module owns the ModConfig dependency.
 */
object VSConfigUpdater {

    @JvmStatic
    val forgeConfigValuesMap: HashMap<String, ModConfigSpec.ConfigValue<*>> = HashMap()

    private val configValueConsumer = { name: String, value: ModConfigSpec.ConfigValue<*> ->
        forgeConfigValuesMap[name] = value
    }

    /**
     * This stores us a map of [ConfigType] -> `Category.value` -> (VSConfigEntry, ForgeConfigEntry)
     * for the sole purpose of the [ConfigCommand] being able to get/edit values with the nicer
     * path with categories included.
     *
     * [forgeConfigValuesMap] is what we actually use for the config api,
     * but it doesn't differentiate between config types and categories.
     * It's just a map of rawPropertyName -> ForgeConfigEntry
     */
    @JvmStatic
    val pathAwareConfigValuesMap: HashMap<ConfigType, HashMap<String, Pair<VsiConfigModelEntry<*>, ModConfigSpec.ConfigValue<*>>>> = HashMap()

    /**
     * @see pathAwareConfigValuesMap
     */
    private fun pathAwareConsumerFor(configType: ConfigType) =
        { path: List<String>, entry: VsiConfigModelEntry<*>, forgeValue: ModConfigSpec.ConfigValue<*> ->
            val path = if (path.size == 1) { listOf("General") + path } else { path }
            pathAwareConfigValuesMap.getOrPut(configType) { HashMap() }
                .set(sanitiseCategoryName(path.joinToString(".")), Pair(entry, forgeValue))
        }

    private fun sanitiseCategoryName(category: String): String {
        return category.replace(" ", "").filter { it.isLetterOrDigit() || it == '.' }
    }

    private val core_server_config = ValkyrienSkiesMod.vsCore.getServerConfig()
    val CORE_SERVER_SPEC: ModConfigSpec = buildModConfigSpec(
        configCategory = core_server_config.root,
        builder = ModConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.CORE_SERVER)
    ).build()

    private val server_config = VSConfigApi.buildVSConfigModel(VSGameConfig.SERVER)
    val SERVER_SPEC: ModConfigSpec = buildModConfigSpec(
        configCategory = server_config.root,
        builder = ModConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.SERVER)
    ).build()

    private val common_config = VSConfigApi.buildVSConfigModel(VSGameConfig.COMMON)
    val COMMON_SPEC: ModConfigSpec = buildModConfigSpec(
        configCategory = common_config.root,
        builder = ModConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.COMMON)
    ).build()

    private val client_config = VSConfigApi.buildVSConfigModel(VSGameConfig.CLIENT)
    val CLIENT_SPEC: ModConfigSpec = buildModConfigSpec(
        configCategory = client_config.root,
        builder = ModConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.CLIENT)
    ).build()

    /**
     * Called by the platform module from a NeoForge ModConfigEvent.Loading/Reloading listener.
     * [spec] is used to dispatch to the right VsiConfigModel; [getByKey] reads the current
     * value from the loaded NightConfig backing the event's ModConfig.
     */
    fun applyFromConfigLoad(spec: ModConfigSpec, getByKey: (String) -> Any?) {
        val (model, type) = when (spec) {
            CORE_SERVER_SPEC -> core_server_config to ConfigType.CORE_SERVER
            SERVER_SPEC -> server_config to ConfigType.SERVER
            COMMON_SPEC -> common_config to ConfigType.COMMON
            CLIENT_SPEC -> client_config to ConfigType.CLIENT
            else -> return
        }
        val updatedEntries = mutableSetOf<ConfigUpdateEntry>()
        model.applyToModel(getByKey, type, updatedEntries)
        if (updatedEntries.isNotEmpty()) VSGameEvents.configUpdated.emit(updatedEntries)
    }
}
