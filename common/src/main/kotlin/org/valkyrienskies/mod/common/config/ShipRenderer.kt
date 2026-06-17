package org.valkyrienskies.mod.common.config

import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.util.settings

enum class ShipRenderer {
    VANILLA,
    FLYWHEEL,
    /**
     * Purpose-built batched ship renderer (the default): meshes each ship into its
     * own VertexBuffers and draws it with one model matrix per ship, and bakes the
     * "fancy" features (tilt-correct shading, world->ship and ship->world lighting)
     * in natively and on by default — not as Sodium-only opt-in flags. Built-in;
     */
    BATCHED
}

val ClientShip.shipRenderer: ShipRenderer
    get() = settings.renderer ?: VSGameConfig.CLIENT.defaultRenderer

val ClientShip.usesTerrainChunkRenderer: Boolean
    get() = shipRenderer == ShipRenderer.VANILLA

val ClientShip.usesBatchedRenderer: Boolean
    get() = shipRenderer == ShipRenderer.BATCHED
