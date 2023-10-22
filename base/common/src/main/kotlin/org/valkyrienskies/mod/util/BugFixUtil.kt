package org.valkyrienskies.mod.util

import net.minecraft.world.phys.AABB

object BugFixUtil {
    fun isCollisionBoxToBig(aabb: AABB): Boolean {
        return aabb.xsize > 1000 || aabb.ysize > 1000 || aabb.zsize > 1000
    }
}
