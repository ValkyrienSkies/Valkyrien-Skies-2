package org.valkyrienskies.mod.common.util


import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Quaternion
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import org.joml.*
import org.valkyrienskies.mod.mixin.util.math.Matrix4fAccessor
import net.minecraft.util.math.Matrix4f as Matrix4fMC

// region JOML

fun Vector3i.set(v: Vec3i) = also {
    x = v.x
    y = v.y
    z = v.z
}

fun Vector3d.set(v: Vec3i) = also {
    x = v.x.toDouble()
    y = v.y.toDouble()
    z = v.z.toDouble()
}

fun Vector3d.set(v: Vec3d) = also {
    x = v.x
    y = v.y
    z = v.z
}


fun Vector3ic.toBlockPos() = BlockPos(x(), y(), z())
fun Vector3dc.toVec3d() = Vec3d(x(), y(), z())

fun Quaternionfc.toMinecraft() = Quaternion(x(), y(), z(), w())

fun Matrix4fc.toMinecraft() = Matrix4fMC().set(this)

// endregion

// region Minecraft

fun MatrixStack.multiply(m: Matrix4dc) = also {
    val quaternion = m.getNormalizedRotation(Quaternionf())
    val translation = m.getTranslation(Vector3d())
    this.multiply(quaternion.toMinecraft())
    this.translate(translation.x, translation.y, translation.z)
}

fun Vec3i.toJOML() = Vector3i().set(this)
fun Vec3i.toJOMLD() = Vector3d().set(this)
fun Vec3d.toJOML() = Vector3d().set(this)

fun Matrix4fMC.set(m: Matrix4fc) = also {
    this as Matrix4fAccessor
    setA00(m.m00())
    setA01(m.m10())
    setA02(m.m20())
    setA03(m.m30())
    setA10(m.m01())
    setA11(m.m11())
    setA12(m.m21())
    setA13(m.m31())
    setA20(m.m02())
    setA21(m.m12())
    setA22(m.m22())
    setA23(m.m32())
    setA30(m.m03())
    setA31(m.m13())
    setA32(m.m23())
    setA33(m.m33())
}

fun Matrix4fMC.set(m: Matrix4dc) = also {
    this as Matrix4fAccessor
    setA00(m.m00().toFloat())
    setA01(m.m10().toFloat())
    setA02(m.m20().toFloat())
    setA03(m.m30().toFloat())
    setA10(m.m01().toFloat())
    setA11(m.m11().toFloat())
    setA12(m.m21().toFloat())
    setA13(m.m31().toFloat())
    setA20(m.m02().toFloat())
    setA21(m.m12().toFloat())
    setA22(m.m22().toFloat())
    setA23(m.m32().toFloat())
    setA30(m.m03().toFloat())
    setA31(m.m13().toFloat())
    setA32(m.m23().toFloat())
    setA33(m.m33().toFloat())
}

// endregion