package org.valkyrienskies.mod.common.util

import com.fasterxml.jackson.annotation.JsonIgnore
import net.minecraft.world.level.border.WorldBorder
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.mod.common.config.VSGameConfig
import kotlin.math.abs

class VoidAttachment : ShipPhysicsListener {
    @Volatile
    var disableOverride = false

    @Volatile
    @JsonIgnore
    var lowestHeight: Int? = null

    @OptIn(PhysTickOnly::class)
    override fun physTick(
        physShip: PhysShip, physLevel: PhysLevel
    ) {
        if (disableOverride) return
        if (lowestHeight == null) return

        val yPos = physShip.transform.positionInWorld.y()

        // If lowestHeight has somehow been concurrently modified
        // to be null between the null check and here, we're cooked
        var distance = yPos - (lowestHeight!! + VSGameConfig.SERVER.voidShipOffset)

        if (distance > 0) return

        distance = abs(distance)//.coerceAtMost(50.0)
        var upVec = Vector3d(0.0, 10.0, 0.0)
        upVec = upVec.add(Vector3d(0.0, distance, 0.0))

        physShip.applyWorldForceToModelPos(physShip.velocity.negate(Vector3d()).mul(physShip.mass))
        physShip.applyWorldForceToModelPos(upVec.mul(physShip.mass))

    }
}
