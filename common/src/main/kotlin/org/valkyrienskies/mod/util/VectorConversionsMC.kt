package org.valkyrienskies.mod.common.util

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Quaternion
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.joml.Matrix4fc
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Quaternionfc
import org.joml.Vector2i
import org.joml.Vector2ic
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector3ic
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.mod.mixin.accessors.util.math.Matrix4fAccessor
import com.mojang.math.Matrix4f as Matrix4fMC

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

fun Vector3f.set(v: Vec3i) = also {
    x = v.x.toFloat()
    y = v.y.toFloat()
    z = v.z.toFloat()
}

fun Vector3d.set(v: Position) = also {
    x = v.x()
    y = v.y()
    z = v.z()
}

fun Vec3i.toDoubles() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun AABBd.set(v: AABB) = also {
    minX = v.minX
    minY = v.minY
    minZ = v.minZ
    maxX = v.maxX
    maxY = v.maxY
    maxZ = v.maxZ
}

fun Vector3ic.toBlockPos() = BlockPos(x(), y(), z())
fun Vector3dc.toMinecraft() = Vec3(x(), y(), z())

fun Quaternionfc.toMinecraft() = Quaternion(x(), y(), z(), w())
fun Quaterniondc.toMinecraft() = Quaternion(x().toFloat(), y().toFloat(), z().toFloat(), w().toFloat())

fun Matrix4fc.toMinecraft() = Matrix4fMC().set(this)
fun Matrix4dc.toMinecraft() = Matrix4fMC().set(this)

fun Matrix4fMC.toJOML() = Matrix4d().set(this)

fun AABBdc.toMinecraft() = AABB(minX(), minY(), minZ(), maxX(), maxY(), maxZ())
fun AABB.toJOML() = AABBd().set(this)

fun Vector2ic.toMinecraft() = ChunkPos(x(), y())
fun ChunkPos.toJOML() = Vector2i().set(this)

fun Vector2i.set(pos: ChunkPos) = also {
    x = pos.x
    y = pos.z
}

@JvmOverloads
fun Matrix4dc.transformDirection(v: Vec3i, dest: Vector3d = Vector3d()) =
    transformDirection(dest.set(v.x.toDouble(), v.y.toDouble(), v.z.toDouble()))

@JvmOverloads
fun Matrix4dc.transformDirection(dir: Direction, dest: Vector3d = Vector3d()) = transformDirection(dir.normal, dest)

// endregion

// region Minecraft

fun PoseStack.multiply(modelTransform: Matrix4dc, normalTransform: Quaterniondc) = also {
    val last = last()
    last.pose().multiply(modelTransform.toMinecraft())
    last.normal().mul(normalTransform.toMinecraft())
}

fun Matrix4fMC.multiply(m: Matrix4dc): Matrix4fMC = also {
    multiply(m.toMinecraft())
}

fun Vec3i.toJOML() = Vector3i().set(this)
fun Vec3i.toJOMLD() = Vector3d().set(this)
fun Vec3i.toJOMLF() = Vector3f().set(this)

fun Position.toJOML() = Vector3d().set(this)

fun Quaternion.toJOML() = Quaterniond(i().toDouble(), j().toDouble(), k().toDouble(), r().toDouble())

fun Quaternion.set(source: Quaterniondc) =
    set(source.x().toFloat(), source.y().toFloat(), source.z().toFloat(), source.w().toFloat())

fun Matrix4fMC.set(m: Matrix4fc) = also {
    @Suppress("CAST_NEVER_SUCCEEDS")
    this as Matrix4fAccessor
    m00 = m.m00()
    m01 = m.m10()
    m02 = m.m20()
    m03 = m.m30()
    m10 = m.m01()
    m11 = m.m11()
    m12 = m.m21()
    m13 = m.m31()
    m20 = m.m02()
    m21 = m.m12()
    m22 = m.m22()
    m23 = m.m32()
    m30 = m.m03()
    m31 = m.m13()
    m32 = m.m23()
    m33 = m.m33()
}

fun Matrix4fMC.set(m: Matrix4dc) = also {
    @Suppress("CAST_NEVER_SUCCEEDS")
    this as Matrix4fAccessor
    m00 = m.m00().toFloat()
    m01 = m.m10().toFloat()
    m02 = m.m20().toFloat()
    m03 = m.m30().toFloat()
    m10 = m.m01().toFloat()
    m11 = m.m11().toFloat()
    m12 = m.m21().toFloat()
    m13 = m.m31().toFloat()
    m20 = m.m02().toFloat()
    m21 = m.m12().toFloat()
    m22 = m.m22().toFloat()
    m23 = m.m32().toFloat()
    m30 = m.m03().toFloat()
    m31 = m.m13().toFloat()
    m32 = m.m23().toFloat()
    m33 = m.m33().toFloat()
}

fun Matrix4d.set(m: Matrix4fMC) = also {
    @Suppress("CAST_NEVER_SUCCEEDS")
    m as Matrix4fAccessor
    m00(m.m00.toDouble())
    m01(m.m10.toDouble())
    m02(m.m20.toDouble())
    m03(m.m30.toDouble())
    m10(m.m01.toDouble())
    m11(m.m11.toDouble())
    m12(m.m21.toDouble())
    m13(m.m31.toDouble())
    m20(m.m02.toDouble())
    m21(m.m12.toDouble())
    m22(m.m22.toDouble())
    m23(m.m32.toDouble())
    m30(m.m03.toDouble())
    m31(m.m13.toDouble())
    m32(m.m23.toDouble())
    m33(m.m33.toDouble())
}

// endregion
