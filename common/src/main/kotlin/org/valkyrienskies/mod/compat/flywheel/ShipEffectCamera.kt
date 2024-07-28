package org.valkyrienskies.mod.compat.flywheel

import net.minecraft.client.Camera
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.util.toJOML
import kotlin.math.atan2
import kotlin.math.sqrt

class ShipEffectCamera(val ship: ClientShip) : Camera() {
    fun update(camera: Camera) {
        val newPos = ship.renderTransform.worldToShip.transformPosition(camera.position.toJOML())
        setPosition(newPos.x(), newPos.y(), newPos.z())

        val direction = ship.renderTransform.worldToShip.transformDirection(camera.lookVector)
        val yaw = -atan2(direction.x, direction.z)
        val pitch = -atan2(direction.y, sqrt((direction.x * direction.x) + (direction.z * direction.z)))
        setRotation((yaw * (180 / Math.PI)).toFloat(), (pitch * (180 / Math.PI)).toFloat())
    }
}
