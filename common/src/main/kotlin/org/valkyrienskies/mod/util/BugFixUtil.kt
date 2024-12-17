package org.valkyrienskies.mod.util

import net.minecraft.world.phys.AABB

object BugFixUtil {
    fun isCollisionBoxTooBig(aabb: AABB): Boolean {
        // This previously used to check aabb.xsize > 1000 && aabb.ysize > 1000 && aabb.zsize > 1000
        // However, that breaks because beacons on large worlds query a 1x1 collision box up to world height
        // We'll check using the area instead.
        return aabb.xsize * aabb.ysize * aabb.zsize > 100_000_000
    }
}
