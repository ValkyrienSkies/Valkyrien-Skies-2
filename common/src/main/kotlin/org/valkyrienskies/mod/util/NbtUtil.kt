package org.valkyrienskies.mod.util

import net.minecraft.nbt.CompoundTag
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc

fun CompoundTag.putVector3d(prefix: String, vector3d: Vector3dc) =
    with(vector3d) {
        putDouble(prefix + "x", x())
        putDouble(prefix + "y", y())
        putDouble(prefix + "z", z())
    }

fun CompoundTag.getVector3d(prefix: String): Vector3d? {
    return if (
        !this.contains(prefix + "x") ||
        !this.contains(prefix + "y") ||
        !this.contains(prefix + "z")
    ) {
        null
    } else {
        Vector3d(
            this.getDouble(prefix + "x"),
            this.getDouble(prefix + "y"),
            this.getDouble(prefix + "z")
        )
    }
}

fun CompoundTag.putQuatd(prefix: String, quat: Quaterniondc) =
    with(quat) {
        putDouble(prefix + "x", x())
        putDouble(prefix + "y", y())
        putDouble(prefix + "z", z())
        putDouble(prefix + "w", w())
    }

fun CompoundTag.getQuatd(prefix: String): Quaterniond? =
    if (
        this.contains(prefix + "x") &&
        this.contains(prefix + "y") &&
        this.contains(prefix + "z") &&
        this.contains(prefix + "w")
    ) {
        Quaterniond(
            getDouble(prefix + "x"),
            getDouble(prefix + "y"),
            getDouble(prefix + "z"),
            getDouble(prefix + "w"),
        )
    } else null
