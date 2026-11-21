package org.valkyrienskies.mod.api.render

import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.culling.Frustum
import org.jetbrains.annotations.ApiStatus.Experimental
import org.joml.Matrix4fc
import org.valkyrienskies.core.api.ships.ClientShip

/** One world render invocation. The camera and culling frustum are borrowed, read-only for the duration of the view. */
@Experimental
class ShipRenderView internal constructor(
    val level: ClientLevel,
    val camera: Camera,
    val viewMatrix: Matrix4fc,
    val projectionMatrix: Matrix4fc,
    val frustum: Frustum,
    val partialTick: Float
)

@Experimental
fun interface ShipRenderPredicate {
    fun shouldRenderShip(ship: ClientShip, view: ShipRenderView): Boolean
}
