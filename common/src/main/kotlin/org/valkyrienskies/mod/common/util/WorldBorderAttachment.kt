package org.valkyrienskies.mod.common.util

import com.fasterxml.jackson.annotation.JsonIgnore
import net.minecraft.world.level.border.WorldBorder
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import kotlin.math.abs

class WorldBorderAttachment : ShipPhysicsListener {
    @Volatile
    @JsonIgnore
    var border = WorldBorder()

    @OptIn(PhysTickOnly::class)
    override fun physTick(
        physShip: PhysShip, physLevel: PhysLevel
    ) {
        val pos = physShip.transform.positionInWorld

        if (!border.isWithinBounds(pos.x(), pos.z(), 0.0)) {
            var distance = border.getDistanceToBorder(pos.x(), pos.z())

            if (distance > 0) return
            if (abs(distance) < border.damageSafeZone) return

            distance = abs(distance) - border.damageSafeZone

            // Prevent MASSIVE forces when really far from border
            // Why are we reusing border.damagePerBlock? because it's roughly the same purpose
            // default damagePerBlock is 0.2, making our max distance force happen at 10 blocks out
            distance = distance.coerceAtMost(border.damagePerBlock * 50)

            // Get a rough vector that will send the ship back inside the border
            val centerVec = Vector3d(border.centerX, pos.y(), border.centerZ)
            var toCenter = centerVec.sub(pos, Vector3d())

            toCenter = toCenter.mul(distance).mul(physShip.mass)
            physShip.applyWorldForceToModelPos(toCenter)
        }
    }
}
