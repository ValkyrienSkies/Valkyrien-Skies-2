package org.valkyrienskies.mod.util

import net.minecraft.world.phys.AABB

fun AABB.scale(scale: Double): AABB {
    val sizeX = (this.xsize * scale) / 2
    val sizeY = (this.ysize * scale) / 2
    val sizeZ = (this.zsize * scale) / 2
    return AABB(
        this.center.x - sizeX,
        this.center.y - sizeY,
        this.center.z - sizeZ,
        this.center.x + sizeX,
        this.center.y + sizeY,
        this.center.z + sizeZ
    )
}
