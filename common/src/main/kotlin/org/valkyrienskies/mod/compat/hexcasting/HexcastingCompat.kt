package org.valkyrienskies.mod.compat.hexcasting

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment

object HexcastingCompat {
    fun register(clazz: Class<out ShipAmbit>) {
        CastingEnvironment.addCreateEventListener { env, _ ->
            env.addExtension(clazz.getConstructor(CastingEnvironment::class.java).newInstance(env) as ShipAmbit)
        }
    }
}
