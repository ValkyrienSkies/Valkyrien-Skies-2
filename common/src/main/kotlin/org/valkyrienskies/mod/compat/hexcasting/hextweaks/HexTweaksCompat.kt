package org.valkyrienskies.mod.compat.hexcasting.hextweaks

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import net.minecraft.world.phys.Vec3
import net.walksanator.hextweaks.casting.environment.ComputerCastingEnv

object HexTweaksCompat {
    fun getComputerPosition(env: CastingEnvironment): Vec3? {
        if (env is ComputerCastingEnv)
            env.pocketData?.let { data ->
                return data.position
            } ?: env.turtleData?.let { data ->
                return data.first.position.center
            }
        return null
    }
}
