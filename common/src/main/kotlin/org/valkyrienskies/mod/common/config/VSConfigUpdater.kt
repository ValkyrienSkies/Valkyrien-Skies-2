package org.valkyrienskies.mod.common.config

import com.electronwill.nightconfig.core.UnmodifiableConfig
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.config.ModConfig
import org.jetbrains.annotations.ApiStatus
import org.valkyrienskies.core.internal.config.VsiConfigModel
import org.valkyrienskies.core.internal.config.VsiConfigModelEntry
import org.valkyrienskies.mod.api.config.VSConfigApi
import org.valkyrienskies.mod.api.config.VSConfigApi.buildForgeConfigSpec
import org.valkyrienskies.mod.api.config.VSConfigApi.updateFrom
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ConfigUpdateEntry
import org.valkyrienskies.mod.common.command.commands.ConfigCommand

object VSConfigUpdater {

    @JvmStatic
    val forgeConfigValuesMap: HashMap<String, ForgeConfigSpec.ConfigValue<*>> = HashMap()

    private val configValueConsumer = { name: String, value: ForgeConfigSpec.ConfigValue<*> ->
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
    val pathAwareConfigValuesMap: HashMap<ConfigType, HashMap<String, Pair<VsiConfigModelEntry<*>, ForgeConfigSpec.ConfigValue<*>>>> = HashMap()

    /**
     * @see pathAwareConfigValuesMap
     */
    private fun pathAwareConsumerFor(configType: ConfigType) =
        { path: List<String>, entry: VsiConfigModelEntry<*>, forgeValue: ForgeConfigSpec.ConfigValue<*> ->
            val path = if (path.size == 1) { listOf("General") + path } else { path }
            pathAwareConfigValuesMap.getOrPut(configType) { HashMap() }
                .set(sanitiseCategoryName(path.joinToString(".")), Pair(entry, forgeValue))
        }

    private fun sanitiseCategoryName(category: String): String {
        return category.replace(" ", "").filter { it.isLetterOrDigit() || it == '.' }
    }

    private val core_server_config = ValkyrienSkiesMod.vsCore.getServerConfig()
    val CORE_SERVER_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = core_server_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.CORE_SERVER),
    ).build().also { CoreConfigMigration.coreServerSpec = it }

    private val core_client_config = ValkyrienSkiesMod.vsCore.getClientConfig()
    val CORE_CLIENT_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = core_client_config.root,
        builder = ForgeConfigSpec.Builder(),
        // Not in forgeConfigValuesMap: its keys are bare names, and these repeat core server names (udpNetworking).
        forgeConfigValueConsumer = { _, _ -> },
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.CORE_CLIENT),
    ).build()

    private val server_config = VSConfigApi.buildVSConfigModel(VSGameConfig.SERVER)
    val SERVER_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = server_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.SERVER),
    ).build()

    private val common_config = VSConfigApi.buildVSConfigModel(VSGameConfig.COMMON)
    val COMMON_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = common_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.COMMON),
    ).build()

    private val client_config = VSConfigApi.buildVSConfigModel(VSGameConfig.CLIENT)
    val CLIENT_SPEC: ForgeConfigSpec = buildForgeConfigSpec(
        configCategory = client_config.root,
        builder = ForgeConfigSpec.Builder(),
        forgeConfigValueConsumer = configValueConsumer,
        pathAwareConsumer = pathAwareConsumerFor(ConfigType.CLIENT),
    ).build()

    private val models = listOf(
        ConfigModel(CORE_SERVER_SPEC, core_server_config, ConfigType.CORE_SERVER),
        ConfigModel(CORE_CLIENT_SPEC, core_client_config, ConfigType.CORE_CLIENT),
        ConfigModel(SERVER_SPEC, server_config, ConfigType.SERVER),
        ConfigModel(COMMON_SPEC, common_config, ConfigType.COMMON),
        ConfigModel(CLIENT_SPEC, client_config, ConfigType.CLIENT),
    )

    /**
     * Call this from platform events when config is loaded or updated
     **/
    @ApiStatus.Internal
    fun update(config: ModConfig) {
        val updatedEntries = mutableSetOf<ConfigUpdateEntry>()

        updateModelOf(config.getSpec(), config.configData, models, updatedEntries)

        if (updatedEntries.isNotEmpty()) {
            VSGameEvents.configUpdated.emit(updatedEntries)
        }
    }
}

/** A config file's spec, and the model it loads into. */
internal class ConfigModel(val spec: ForgeConfigSpec, val model: VsiConfigModel, val type: ConfigType)

/**
 * Updates only the model loaded from [spec]'s file: models share option names (the core client and server configs
 * both have `udpNetworking`), so data from one file must never reach another file's model.
 */
internal fun updateModelOf(spec: Any, data: UnmodifiableConfig, models: List<ConfigModel>,
                           updatedEntries: MutableSet<ConfigUpdateEntry>) {
    for (m in models) if (m.spec === spec) m.model.updateFrom(data, m.type, updatedEntries)
}
