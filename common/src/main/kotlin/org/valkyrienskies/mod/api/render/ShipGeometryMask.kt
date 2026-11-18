package org.valkyrienskies.mod.api.render

import org.jetbrains.annotations.ApiStatus.Experimental

/** Borrowed R8 texture in framebuffer coordinates, with the origin at the bottom left.
 * Coverage is zero outside the bounds. Overlapping surfaces contribute their maximum opacity.
 * The texture is valid until this world view finishes rendering.
 */
@Experimental
class ShipGeometryMask internal constructor(
    val textureId: Int, val textureWidth: Int, val textureHeight: Int,
    val x: Int, val y: Int, val width: Int, val height: Int
)
