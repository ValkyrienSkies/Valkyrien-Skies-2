package org.valkyrienskies.mod.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId

/**
 * Exists basically purely to be able to use breakpoints on this code
 */
object PhysicsBlockEntityUtil {

    @JvmStatic
    fun onLoad(listener: BlockEntityPhysicsListener, pos: BlockPos, level: Level, reason: String = "") {
        ValkyrienSkiesMod.addBlockEntityPhysTicker(level.dimensionId, pos, listener)
        //temp
        // println("$listener [$pos], $reason")
    }


    @JvmStatic
    fun onRemove(pos: BlockPos, level: Level, reason: String = "") {
        ValkyrienSkiesMod.removeBlockEntityPhysTicker(pos, level.dimensionId)
        // println("[$pos], $reason")
    }
}
