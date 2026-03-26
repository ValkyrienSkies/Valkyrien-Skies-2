package org.valkyrienskies.mod.compat.hexcasting.hexal

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.walksanator.hextweaks.casting.environment.ComputerCastingEnv
import ram.talia.hexal.api.casting.eval.env.WispCastEnv

object HexalCompat {
    fun getWispPosition(env: CastingEnvironment): Vec3? {
        if (env is WispCastEnv)
            return (env.wisp as Entity).position()
        return null
    }
}
