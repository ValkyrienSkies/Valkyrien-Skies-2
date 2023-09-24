@file:JvmName("LiquidState")
package org.valkyrienskies.mod.api.registration

import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.mod.api.internal.require4bits

fun buildLiquidState(block: LiquidState.Builder.() -> Unit): LiquidState =
    LiquidState.Builder().apply(block).build()

class LiquidState private constructor(
    val boundingBox: AABBic,
    val density: Float,
    val dragCoefficient: Float,
    val fluidVel: Vector3dc,
) {

    fun toBuilder(): Builder = Builder().apply {
        boundingBox(boundingBox)
        density(density)
        dragCoefficient(dragCoefficient)
        fluidVel(fluidVel)
    }

    class Builder {
        private val boundingBox: AABBi = AABBi(0, 0, 0, 15, 15, 15)
        private var density: Float? = null
        private var dragCoefficient: Float? = null
        private var _fluidVel: Vector3d = Vector3d()

        fun boundingBox(boundingBox: AABBic) = also {
            require4bits("boundingBox", boundingBox)
            this.boundingBox.set(boundingBox)
        }

        fun density(density: Float) = also { this.density = density }
        fun dragCoefficient(dragCoefficient: Float) = also { this.dragCoefficient = dragCoefficient }
        fun fluidVel(fluidVel: Vector3dc) = also { this._fluidVel.set(fluidVel) }

        fun build(): LiquidState {
            val density = density
            val dragCoefficient = dragCoefficient

            requireNotNull(density) { "density must be set" }
            requireNotNull(dragCoefficient) { "dragCoefficient must be set" }

            return LiquidState(boundingBox, density, dragCoefficient, _fluidVel)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidState) return false

        if (boundingBox != other.boundingBox) return false
        if (density != other.density) return false
        if (dragCoefficient != other.dragCoefficient) return false
        if (fluidVel != other.fluidVel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = boundingBox.hashCode()
        result = 31 * result + density.hashCode()
        result = 31 * result + dragCoefficient.hashCode()
        result = 31 * result + fluidVel.hashCode()
        return result
    }

    override fun toString(): String {
        return "LiquidState(boundingBox=$boundingBox, density=$density, dragCoefficient=$dragCoefficient, fluidVel=$fluidVel)"
    }
}

