package org.valkyrienskies.mod.common.util


import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4dc
import org.joml.Matrix4fc
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.mod.mixin.util.math.Matrix4fAccessor
import net.minecraft.util.math.Matrix4f as Matrix4fMC

// region JOML

fun Vector3i.set(v: Vec3i): Vector3i = also {
    x = v.x
    y = v.y
    z = v.z
}

fun Vector3ic.toBlockPos() = BlockPos(x(), y(), z())

// endregion

// region Minecraft

fun Vec3i.toJOML(): Vector3i = Vector3i().set(this)

fun Matrix4fMC.set(m: Matrix4fc) = also {
    setMatrix4fFromJOML(m, this)
}

fun Matrix4fMC.set(m: Matrix4dc) = also {
    setMatrix4fFromJOML(m, this)
}

fun setMatrix4fFromJOML(source: Matrix4dc, dest: Matrix4fMC): Matrix4fMC {
    @Suppress("CAST_NEVER_SUCCEEDS") val matrix4fAccessor = dest as Matrix4fAccessor
    matrix4fAccessor.setA00(source.m00().toFloat())
    matrix4fAccessor.setA01(source.m10().toFloat())
    matrix4fAccessor.setA02(source.m20().toFloat())
    matrix4fAccessor.setA03(source.m30().toFloat())
    matrix4fAccessor.setA10(source.m01().toFloat())
    matrix4fAccessor.setA11(source.m11().toFloat())
    matrix4fAccessor.setA12(source.m21().toFloat())
    matrix4fAccessor.setA13(source.m31().toFloat())
    matrix4fAccessor.setA20(source.m02().toFloat())
    matrix4fAccessor.setA21(source.m12().toFloat())
    matrix4fAccessor.setA22(source.m22().toFloat())
    matrix4fAccessor.setA23(source.m32().toFloat())
    matrix4fAccessor.setA30(source.m03().toFloat())
    matrix4fAccessor.setA31(source.m13().toFloat())
    matrix4fAccessor.setA32(source.m23().toFloat())
    matrix4fAccessor.setA33(source.m33().toFloat())
    return dest
}

fun setMatrix4fFromJOML(source: Matrix4fc, dest: Matrix4fMC): Matrix4fMC {
    @Suppress("CAST_NEVER_SUCCEEDS") val matrix4fAccessor = dest as Matrix4fAccessor
    matrix4fAccessor.setA00(source.m00())
    matrix4fAccessor.setA01(source.m10())
    matrix4fAccessor.setA02(source.m20())
    matrix4fAccessor.setA03(source.m30())
    matrix4fAccessor.setA10(source.m01())
    matrix4fAccessor.setA11(source.m11())
    matrix4fAccessor.setA12(source.m21())
    matrix4fAccessor.setA13(source.m31())
    matrix4fAccessor.setA20(source.m02())
    matrix4fAccessor.setA21(source.m12())
    matrix4fAccessor.setA22(source.m22())
    matrix4fAccessor.setA23(source.m32())
    matrix4fAccessor.setA30(source.m03())
    matrix4fAccessor.setA31(source.m13())
    matrix4fAccessor.setA32(source.m23())
    matrix4fAccessor.setA33(source.m33())
    return dest
}

// endregion