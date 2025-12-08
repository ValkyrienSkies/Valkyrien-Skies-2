package org.valkyrienskies.mod.compat.hexcasting

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment

object HexcastingCompat {
    fun register() {
        CastingEnvironment.addCreateEventListener { env, _ ->
            env.addExtension(AmbitRemapping(env))
        }
    }
}
