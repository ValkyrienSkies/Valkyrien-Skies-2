package org.valkyrienskies.mod.common.config

import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.primitives.AABBi
import org.valkyrienskies.core.api.physics.blockstates.FluidBoundaryShape
import java.util.function.Consumer
import kotlin.math.ceil
import kotlin.math.floor

internal object FluidBoundaryRegistration {
    fun fromShape(shape: VoxelShape): FluidBoundaryShape {
        val boxes = ArrayList<AABBi>()
        return if (forEachCellBox(shape, boxes::add)) FluidBoundaryShape.fromBoxes(boxes) else FluidBoundaryShape.CLOSED
    }

    @JvmStatic
    fun forEachCellBox(shape: VoxelShape, consume: Consumer<AABBi>): Boolean {
        var valid = true
        shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
            if (!minX.isFinite() || !minY.isFinite() || !minZ.isFinite() ||
                !maxX.isFinite() || !maxY.isFinite() || !maxZ.isFinite()) {
                valid = false
            } else {
                val x0 = maxOf(0.0, minX); val x1 = minOf(1.0, maxX)
                val y0 = maxOf(0.0, minY); val y1 = minOf(1.0, maxY)
                val z0 = maxOf(0.0, minZ); val z1 = minOf(1.0, maxZ)
                if (x0 < x1 && y0 < y1 && z0 < z1) {
                    consume.accept(AABBi(
                        floor(x0 * 16).toInt(), floor(y0 * 16).toInt(), floor(z0 * 16).toInt(),
                        ceil(x1 * 16).toInt() - 1, ceil(y1 * 16).toInt() - 1, ceil(z1 * 16).toInt() - 1
                    ))
                }
            }
        }
        return valid
    }
}
