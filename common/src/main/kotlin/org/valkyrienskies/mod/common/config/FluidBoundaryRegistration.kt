package org.valkyrienskies.mod.common.config

import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.primitives.AABBi
import org.valkyrienskies.core.api.physics.blockstates.FluidBoundaryShape
import kotlin.math.ceil
import kotlin.math.floor

internal object FluidBoundaryRegistration {
    fun fromShape(shape: VoxelShape): FluidBoundaryShape {
        val boxes = ArrayList<AABBi>()
        var valid = true
        shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
            if (!minX.isFinite() || !minY.isFinite() || !minZ.isFinite() ||
                !maxX.isFinite() || !maxY.isFinite() || !maxZ.isFinite() ||
                minX < 0 || minY < 0 || minZ < 0 || maxX > 1 || maxY > 1 || maxZ > 1) {
                valid = false
            } else if (maxX > minX && maxY > minY && maxZ > minZ) {
                boxes.add(AABBi(
                    floor(minX * 16).toInt(), floor(minY * 16).toInt(), floor(minZ * 16).toInt(),
                    ceil(maxX * 16).toInt() - 1, ceil(maxY * 16).toInt() - 1, ceil(maxZ * 16).toInt() - 1
                ))
            }
        }
        return if (valid) FluidBoundaryShape.fromBoxes(boxes) else FluidBoundaryShape.CLOSED
    }
}
