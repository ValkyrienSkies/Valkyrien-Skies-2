package org.valkyrienskies.mod.forge.common

import org.joml.Matrix3d
import org.valkyrienskies.mod.forge.mixin.compat.create.Matrix3dAccessor

fun com.simibubi.create.foundation.collision.Matrix3d.toJOML(): Matrix3d {
    val accessor = this as Matrix3dAccessor
    return Matrix3d(
        accessor.m00, accessor.m01, accessor.m02,
        accessor.m10, accessor.m11, accessor.m12,
        accessor.m20, accessor.m21, accessor.m22
    )
}
