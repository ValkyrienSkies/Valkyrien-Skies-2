package org.valkyrienskies.mod.client

import org.joml.Vector3dc

object ClipPlanesHandler {
    val clipPlanes = mutableListOf<ClipPlane>()
}

class ClipPlane(val position: Vector3dc, val normal: Vector3dc, val height: Double, val width: Double)
