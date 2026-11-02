package org.valkyrienskies.mod.common.config

import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.util.settings
import org.valkyrienskies.mod.compat.LoadedMods
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat

enum class ShipRenderer {
    VANILLA,
    FLYWHEEL,
    /** Use ship meshes and apply the client rendering settings. */
    BATCHED
}

val ClientShip.shipRenderer: ShipRenderer
    get() = resolveShipRenderer(settings.renderer ?: VSGameConfig.CLIENT.defaultRenderer)

fun resolveShipRenderer(renderer: ShipRenderer): ShipRenderer =
    if (renderer == ShipRenderer.FLYWHEEL &&
        (LoadedMods.flywheel != LoadedMods.FlywheelVersion.V1 || !FlywheelCompat.isBackendAvailable())) {
        ShipRenderer.VANILLA
    } else renderer

val ClientShip.usesTerrainChunkRenderer: Boolean
    get() = shipRenderer == ShipRenderer.VANILLA

val ClientShip.usesBatchedRenderer: Boolean
    get() = shipRenderer == ShipRenderer.BATCHED
