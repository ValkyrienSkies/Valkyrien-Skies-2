package org.valkyrienskies.mod.common.config

import com.electronwill.nightconfig.core.CommentedConfig
import com.electronwill.nightconfig.core.UnmodifiableConfig
import org.valkyrienskies.mod.util.logger

/**
 * Moves options that older vs-core server configs kept at the root of `vs-core-server.toml` into the `[Network]`
 * table, where they live now, so a customised value survives the upgrade.
 *
 * Forge (and Forge Config API Port on Fabric) check a freshly loaded config file against its spec and reset whatever
 * doesn't fit: root keys the spec no longer has would be removed, and the `[Network]` defaults written instead. So
 * this runs on the loaded data just before that check, from `MixinForgeConfigSpec` (on every load of the core server
 * spec: a world's `serverconfig/` file, or a copy of `defaultconfigs/`), and the corrected file is then saved with
 * the migrated values.
 *
 * A root key is moved only if `[Network]` doesn't have it yet; otherwise nothing is touched. `enableUdp` isn't moved:
 * `udpNetworking` replaces it and is on for upgraded servers, so it is just removed. Data without these root keys,
 * e.g. an already migrated or a new file, is left alone.
 */
object CoreConfigMigration {
    /** The spec whose loaded files are migrated ([VSConfigUpdater.CORE_SERVER_SPEC]). */
    @JvmStatic
    @Volatile
    var coreServerSpec: Any? = null

    private const val NETWORK = "Network"
    private val MOVED = listOf("udpPort", "compactTcpShipTransforms")
    private const val REPLACED = "enableUdp"

    private val logger by logger()

    /** Called with every config a Forge config spec is about to accept; migrates it if it's the core server config. */
    @JvmStatic
    fun beforeSpecAccepts(spec: Any, data: CommentedConfig) {
        if (spec === coreServerSpec) migrate(data)
    }

    /** Migrates [data] in place. Returns the keys it moved into `[Network]`. */
    @JvmStatic
    fun migrate(data: CommentedConfig): List<String> {
        val moved = ArrayList<String>()
        for (key in MOVED) {
            val value = data.get<Any>(listOf(key))
            if (value == null || value is UnmodifiableConfig || data.contains(listOf(NETWORK, key))) continue
            data.set<Any>(listOf(NETWORK, key), value)
            data.remove<Any>(listOf(key))
            data.removeComment(listOf(key))
            logger.info("Moved {} = {} into [{}] of the VS core server config", key, value, NETWORK)
            moved += key
        }
        if (data.get<Any>(listOf(REPLACED)) != null) {
            val value = data.remove<Any>(listOf(REPLACED))
            data.removeComment(listOf(REPLACED))
            logger.info("Removed {} = {} from the VS core server config: udpNetworking in [{}] replaces it", REPLACED,
                value, NETWORK)
        }
        return moved
    }
}
