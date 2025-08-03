package org.valkyrienskies.mod.compat

import net.minecraft.core.Direction.Axis
import net.minecraft.core.Vec3i
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object CreateCompat {

    private val contraptionClass = runCatching {
        Class.forName("com.simibubi.create.content.contraptions.AbstractContraptionEntity")
    }.getOrNull()

    @JvmStatic
    fun isContraption(entity: Entity): Boolean {
        return contraptionClass?.isInstance(entity) ?: false
    }

    @JvmStatic
    fun getCenterOf(pos: Vec3i): Vec3 {
        if (pos == Vec3i.ZERO)
            return Vec3(0.5, 0.5, 0.5)
        return Vec3.atLowerCornerOf(pos)
            .add(.5, .5, .5)
    }

    @JvmStatic
    fun rotate(vec: Vec3, deg: Double, axis: Axis): Vec3 {
        if (deg == 0.0)
            return vec
        if (vec == Vec3.ZERO)
            return vec

        val angle = (deg / 180f * Math.PI)
        val sin = sin(angle)
        val cos = cos(angle)
        val x = vec.x
        val y = vec.y
        val z = vec.z

        if (axis == Axis.X)
            return Vec3(x, y * cos - z * sin, z * cos + y * sin)
        if (axis == Axis.Y)
            return Vec3(x * cos + z * sin, y, z * cos - x * sin)
        if (axis == Axis.Z)
            return Vec3(x * cos - y * sin, y * cos + x * sin, z)
        return vec
    }

    @JvmStatic
    fun lerp(p: Float, from: Vec3, to: Vec3): Vec3 {
        return from.add(to.subtract(from)
            .scale(p.toDouble()))
    }
}
