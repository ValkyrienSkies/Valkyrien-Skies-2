package org.valkyrienskies.mod.util


import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import org.joml.Matrix4dc
import org.joml.Matrix4fc
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.mod.MixinInterfaces
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
	this as MixinInterfaces.ISetMatrix4fFromJOML
	`vs$setFromJOML`(m)
}

fun Matrix4fMC.set(m: Matrix4dc) = also {
	this as MixinInterfaces.ISetMatrix4fFromJOML
	`vs$setFromJOML`(m)
}

// endregion