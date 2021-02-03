package org.valkyrienskies.mod.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import org.joml.Vector3i
import org.joml.Vector3ic

// region JOML

infix fun Vector3i.set(v: Vec3i): Vector3i = also {
	x = v.x
	y = v.y
	z = v.z
}

fun Vector3ic.toBlockPos() = BlockPos(x(), y(), z())

// endregion

// region Minecraft

fun Vec3i.toJOML(): Vector3i = Vector3i() set this

// endregion