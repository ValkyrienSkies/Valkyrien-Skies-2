package org.valkyrienskies.mod.common.world

import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.ships.properties.ShipId

data class RayCastExtraParameter(
    var shouldTransformHitPos: Boolean = true,
    var skipShip: ShipId? = null,
    var skipWorld: Boolean = false,
    var useVanillaClip: Boolean = false,
    var _posStart: Vec3? = null,
    var _posEnd: Vec3? = null)
