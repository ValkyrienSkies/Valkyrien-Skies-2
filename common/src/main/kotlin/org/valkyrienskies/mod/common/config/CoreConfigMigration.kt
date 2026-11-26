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
 *
 * `udpPort` used to default to 25565; now its default is 0, "the Minecraft server's port". A `udpPort` of 25565 that
 * nobody chose (a root key, from before `[Network]` existed; or a `[Network]` value still carrying the description
 * of the 25565 default, which Forge rewrites once the file is saved with the current one) becomes 0.
 */
object CoreConfigMigration {
    /** The spec whose loaded files are migrated ([VSConfigUpdater.CORE_SERVER_SPEC]). */
    @JvmStatic
    @Volatile
    var coreServerSpec: Any? = null

    private const val NETWORK = "Network"
    private val MOVED = listOf("udpPort", "compactTcpShipTransforms")
    private const val REPLACED = "enableUdp"
    private const val UDP_PORT = "udpPort"
    private const val OLD_UDP_PORT_DEFAULT = 25565

    /** Part of the description `udpPort` had while 25565 was its default. */
    private const val OLD_UDP_PORT_DESCRIPTION = "The default is 25565"

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
        resetOldUdpPortDefault(data)
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

    private fun resetOldUdpPortDefault(data: CommentedConfig) {
        val root = listOf(UDP_PORT)
        if ((data.get<Any>(root) as? Number)?.toInt() == OLD_UDP_PORT_DEFAULT) {
            data.remove<Any>(root)
            data.removeComment(root)
            logger.info("Removed {} = {} (the old default) from the VS core server config: {} now defaults to the " +
                "Minecraft server's port", UDP_PORT, OLD_UDP_PORT_DEFAULT, UDP_PORT)
        }
        val network = listOf(NETWORK, UDP_PORT)
        if ((data.get<Any>(network) as? Number)?.toInt() == OLD_UDP_PORT_DEFAULT &&
            data.getComment(network)?.contains(OLD_UDP_PORT_DESCRIPTION) == true) {
            data.set<Any>(network, 0)
            logger.info("Set {} = 0 in [{}] of the VS core server config (it was the old default, {}): UDP now uses " +
                "the Minecraft server's port. Set it again if you forward UDP {} on purpose.", UDP_PORT, NETWORK,
                OLD_UDP_PORT_DEFAULT, OLD_UDP_PORT_DEFAULT)
        }
    }
}
