package org.valkyrienskies.mod.api.render

import net.minecraft.resources.ResourceLocation
import org.jetbrains.annotations.ApiStatus.Experimental
import org.valkyrienskies.core.api.ships.ClientShip

@Experimental
class ShipMaterial internal constructor(val id: ResourceLocation, @get:JvmName("getIndex") internal val index: Int)

/**
 * LAYER keeps the source layer's depth and blend state.
 *
 * BLENDED draws the hull during translucent terrain with alpha blending, depth test, and no depth write.
 */
@Experimental
enum class ShipMaterialPass { LAYER, BLENDED }

@Experimental
data class ShipMaterialSelection @JvmOverloads constructor(
    val material: ShipMaterial,
    val pass: ShipMaterialPass = ShipMaterialPass.LAYER,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val w: Float = 0f
)

/** Non-null selections run in selector registration order. Any BLENDED selection moves the stack to that pass. */
@Experimental
fun interface ShipMaterialSelector {
    fun selectShipMaterial(ship: ClientShip, view: ShipRenderView): ShipMaterialSelection?
}
