package org.valkyrienskies.mod.api.registration

import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.mod.api.internal.require4bits

class SolidState private constructor(
    val elasticity: Double,
    val friction: Double,
    val hardness: Double,
    val boundingBox: AABBic,
    val solidBoxes: List<AABBic>,
    val negativeBoxes: List<AABBic>
) {

    class Builder {
        private var elasticity: Double? = null
        private var friction: Double? = null
        private var hardness: Double? = null
        private val solidBoxes = mutableListOf<AABBic>()
        private val negativeBoxes = mutableListOf<AABBic>()

        fun elasticity(elasticity: Double) = also { this.elasticity = elasticity }
        fun friction(friction: Double) = also { this.friction = friction }
        fun hardness(hardness: Double) = also { this.hardness = hardness }
        fun addSolidBox(box: AABBic) = also {
            require4bits("box", box)
            solidBoxes.add(box)
        }
        fun addNegativeBox(box: AABBic) = also {
            require4bits("box", box)
            negativeBoxes.add(box)
        }

        fun build(): SolidState {
            val elasticity = requireNotNull(elasticity) { "elasticity must be set" }
            val friction = requireNotNull(friction) { "friction must be set" }
            val hardness = requireNotNull(hardness) { "hardness must be set" }

            val allBoxes = (solidBoxes + negativeBoxes).fold(AABBi()) { a, b -> a.intersection(b) }
            require(
                allBoxes.minX == 0 &&
                    allBoxes.minY == 0 &&
                    allBoxes.minZ == 0 &&
                    allBoxes.maxX == 15 &&
                    allBoxes.maxY == 15 &&
                    allBoxes.maxZ == 15
            ) { "solid and negative boxes must cover the entire cube" }

            for (solidBox in solidBoxes) {
                val intersects = negativeBoxes.find { solidBox.intersectsAABB(it) }

                require(intersects == null) { "solid box $solidBox must not intersect negative box $intersects" }
            }

            val boundingBox = solidBoxes.fold(AABBi()) { a, b -> a.intersection(b) }

            return SolidState(elasticity, friction, hardness, boundingBox, solidBoxes, negativeBoxes)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolidState) return false

        if (elasticity != other.elasticity) return false
        if (friction != other.friction) return false
        if (hardness != other.hardness) return false
        if (boundingBox != other.boundingBox) return false
        if (solidBoxes != other.solidBoxes) return false
        if (negativeBoxes != other.negativeBoxes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = elasticity.hashCode()
        result = 31 * result + friction.hashCode()
        result = 31 * result + hardness.hashCode()
        result = 31 * result + boundingBox.hashCode()
        result = 31 * result + solidBoxes.hashCode()
        result = 31 * result + negativeBoxes.hashCode()
        return result
    }

    override fun toString(): String {
        return "SolidState(elasticity=$elasticity, friction=$friction, hardness=$hardness, boundingBox=$boundingBox, solidBoxes=$solidBoxes, negativeBoxes=$negativeBoxes)"
    }
}
