package org.valkyrienskies.mod

import net.minecraft.core.BlockPos
import org.joml.Vector3i
import org.joml.Vector3ic

/**
 * The purpose of this object is to convert data between Minecraft data types and JOML data types.
 */
object JOMLConversion {
    fun convertBlockPosToVector3i(blockPos: BlockPos): Vector3i {
        return Vector3i(blockPos.x, blockPos.y, blockPos.z)
    }

    fun convertVector3icToBlockPos(vector3ic: Vector3ic): BlockPos {
        return BlockPos(vector3ic.x(), vector3ic.y(), vector3ic.z())
    }
}