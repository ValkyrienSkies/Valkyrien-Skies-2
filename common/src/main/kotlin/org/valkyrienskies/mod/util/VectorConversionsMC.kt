package org.valkyrienskies.mod.common.util

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.joml.Matrix4fc
import org.joml.Quaterniondc
import org.joml.Quaternionf
import org.joml.Vector2i
import org.joml.Vector2ic
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector3ic
import org.joml.Vector4f
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc

// region JOML

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.set(). " +
        "Remove the import org.valkyrienskies.mod.common.util.set",
    replaceWith = ReplaceWith(
        "this.set(v)",
        "org.valkyrienskies.mod.api.set"
    )
)
fun Vector3i.set(v: Vec3i) = also {
    x = v.x
    y = v.y
    z = v.z
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.set(). " +
        "Remove the import org.valkyrienskies.mod.common.util.set",
    replaceWith = ReplaceWith(
        "this.set(v)",
        "org.valkyrienskies.mod.api.set"
    )
)
fun Vector3d.set(v: Vec3i) = also {
    x = v.x.toDouble()
    y = v.y.toDouble()
    z = v.z.toDouble()
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.set(). " +
        "Remove the import org.valkyrienskies.mod.common.util.set",
    replaceWith = ReplaceWith(
        "this.set(v)",
        "org.valkyrienskies.mod.api.set"
    )
)
fun Vector3f.set(v: Vec3i) = also {
    x = v.x.toFloat()
    y = v.y.toFloat()
    z = v.z.toFloat()
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.set(). " +
        "Remove the import org.valkyrienskies.mod.common.util.set",
    replaceWith = ReplaceWith(
        "this.set(v)",
        "org.valkyrienskies.mod.api.set"
    )
)
fun Vector3d.set(v: Position) = also {
    x = v.x()
    y = v.y()
    z = v.z()
}

fun Vec3i.toDoubles() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.set(). " +
        "Remove the import org.valkyrienskies.mod.common.util.set",
    replaceWith = ReplaceWith(
        "this.set(v)",
        "org.valkyrienskies.mod.api.set"
    )
)
fun AABBd.set(v: AABB) = also {
    minX = v.minX
    minY = v.minY
    minZ = v.minZ
    maxX = v.maxX
    maxY = v.maxY
    maxZ = v.maxZ
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toBlockPos(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toBlockPos",
    replaceWith = ReplaceWith(
        "this.toBlockPos()",
        "org.valkyrienskies.mod.api.toBlockPos"
    )
)
fun Vector3ic.toBlockPos() = BlockPos(x(), y(), z())

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toMinecraft(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toMinecraft",
    replaceWith = ReplaceWith(
        "this.toMinecraft()",
        "org.valkyrienskies.mod.api.toMinecraft"
    )
)
fun Vector3dc.toMinecraft() = Vec3(x(), y(), z())

fun Matrix4d.mul(m: Matrix4fc): Matrix4d = mul(m, this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toMinecraft(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toMinecraft",
    replaceWith = ReplaceWith(
        "this.toMinecraft()",
        "org.valkyrienskies.mod.api.toMinecraft"
    )
)
fun AABBdc.toMinecraft() = AABB(minX(), minY(), minZ(), maxX(), maxY(), maxZ())

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toJOML(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toJOML",
    replaceWith = ReplaceWith(
        "this.toJOML()",
        "org.valkyrienskies.mod.api.toJOML"
    )
)
fun AABB.toJOML() = AABBd().set(this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toChunkPos(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toChunkPos",
    replaceWith = ReplaceWith(
        "this.toChunkPos()",
        "org.valkyrienskies.mod.api.toChunkPos"
    )
)
fun Vector2ic.toMinecraft() = ChunkPos(x(), y())

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toJOML(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toJOML",
    replaceWith = ReplaceWith(
        "this.toJOML()",
        "org.valkyrienskies.mod.api.toJOML"
    )
)
fun ChunkPos.toJOML() = Vector2i().set(this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toJOML(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toJOML",
    replaceWith = ReplaceWith(
        "this.toJOML()",
        "org.valkyrienskies.mod.api.toJOML"
    )
)
fun Vec3.toJOML() = Vector3d().set(this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.set(). " +
        "Remove the import org.valkyrienskies.mod.common.util.set",
    replaceWith = ReplaceWith(
        "this.set(v)",
        "org.valkyrienskies.mod.api.set"
    )
)
fun Vector3d.set(v: Vec3) = also {
    x = v.x
    y = v.y
    z = v.z
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.set(). " +
        "Remove the import org.valkyrienskies.mod.common.util.set",
    replaceWith = ReplaceWith(
        "this.set(pos)",
        "org.valkyrienskies.mod.api.set"
    )
)
fun Vector2i.set(pos: ChunkPos) = also {
    x = pos.x
    y = pos.z
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.transformDirection(). " +
        "Remove the import org.valkyrienskies.mod.common.util.transformDirection",
    replaceWith = ReplaceWith(
        "transformDirection(v, dest)",
        "org.valkyrienskies.mod.api.transformDirection"
    )
)
@JvmOverloads
fun Matrix4dc.transformDirection(v: Vec3i, dest: Vector3d = Vector3d()) =
    transformDirection(dest.set(v.x.toDouble(), v.y.toDouble(), v.z.toDouble()))

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.transformDirection(). " +
        "Remove the import org.valkyrienskies.mod.common.util.transformDirection",
    replaceWith = ReplaceWith(
        "transformDirection(dir, dest)",
        "org.valkyrienskies.mod.api.transformDirection"
    )
)
@JvmOverloads
fun Matrix4dc.transformDirection(dir: Direction, dest: Vector3d = Vector3d()) = transformDirection(dir.normal, dest)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.transform(). " +
        "Remove the import org.valkyrienskies.mod.common.util.transform",
    replaceWith = ReplaceWith(
        "transform(v)",
        "org.valkyrienskies.mod.api.transform"
    )
)
fun Matrix4dc.transform(v: Vector4f) = v.also {
    it.set(
        (m00() * v.x() + m01() * v.y() + m02() * v.z() + m03() * v.w()).toFloat(),
        (m10() * v.x() + m11() * v.y() + m12() * v.z() + m13() * v.w()).toFloat(),
        (m20() * v.x() + m21() * v.y() + m22() * v.z() + m23() * v.w()).toFloat(),
        (m30() * v.x() + m31() * v.y() + m32() * v.z() + m33() * v.w()).toFloat()
    )
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.transformPosition(). " +
        "Remove the import org.valkyrienskies.mod.common.util.transformPosition",
    replaceWith = ReplaceWith(
        "transformPosition(v)",
        "org.valkyrienskies.mod.api.transformPosition"
    )
)
fun Matrix4dc.transformPosition(v: Vec3): Vec3 {
    return transformPosition(v.toJOML()).toMinecraft()
}

// endregion

// region Minecraft

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.multiply(). " +
        "Remove the import org.valkyrienskies.mod.common.util.multiply",
    replaceWith = ReplaceWith(
        "this.multiply(modelTransform, normalTransform)",
        "org.valkyrienskies.mod.api.multiply"
    )
)
fun PoseStack.multiply(modelTransform: Matrix4dc, normalTransform: Quaterniondc) = also {
    val last = last()

    val newPose = Matrix4d().set(last.pose()).mul(modelTransform)
    val newNormal = last.normal().mul(Matrix3f().set(normalTransform))

    last.pose().set(newPose)
    last.normal().set(newNormal)
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.multiply(). " +
        "Remove the import org.valkyrienskies.mod.common.util.multiply",
    replaceWith = ReplaceWith(
        "this.multiply(modelTransform)",
        "org.valkyrienskies.mod.api.multiply"
    )
)
fun PoseStack.multiply(modelTransform: Matrix4dc) = also {
    val last = last()
    val newPose = Matrix4d().set(last.pose()).mul(modelTransform)
    last.pose().set(newPose)
}

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toJOML(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toJOML",
    replaceWith = ReplaceWith(
        "this.toJOML()",
        "org.valkyrienskies.mod.api.toJOML"
    )
)
fun Vec3i.toJOML() = Vector3i().set(this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toJOMLd(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toJOMLd",
    replaceWith = ReplaceWith(
        "this.toJOMLd()",
        "org.valkyrienskies.mod.api.toJOMLd"
    )
)
fun Vec3i.toJOMLD() = Vector3d().set(this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toJOMLf(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toJOMLF",
    replaceWith = ReplaceWith(
        "this.toJOMLf()",
        "org.valkyrienskies.mod.api.toJOMLf"
    )
)
fun Vec3i.toJOMLF() = Vector3f().set(this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toJOML(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toJOML",
    replaceWith = ReplaceWith(
        "this.toJOML()",
        "org.valkyrienskies.mod.api.toJOML"
    )
)
fun Position.toJOML() = Vector3d().set(this)

@Deprecated(
    message = "Moved. Java users switch to ValkyrienSkies.toFloat(). " +
        "Remove the import org.valkyrienskies.mod.common.util.toFloat",
    replaceWith = ReplaceWith(
        "this.toFloat()",
        "org.valkyrienskies.mod.api.toFloat"
    )
)
fun Quaterniondc.toFloat() = Quaternionf(x(), y(), z(), w())
// endregion
