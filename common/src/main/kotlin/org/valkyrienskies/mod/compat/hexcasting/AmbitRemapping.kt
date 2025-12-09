package org.valkyrienskies.mod.compat.hexcasting

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent.IsVecInRange
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent.Key
import at.petrak.hexcasting.api.casting.eval.env.CircleCastEnv
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.xplat.Platform
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.api.positionToShip
import org.valkyrienskies.mod.api.positionToWorld
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.compat.hexcasting.hextweaks.HexTweaksCompat
import kotlin.random.Random

class AmbitRemapping(private val env: CastingEnvironment) : IsVecInRange {
    private val id = Keygen.randid()
    private val key = Key(id)

    override fun getKey(): Key<*> = key

    @OptIn(GameTickOnly::class)
    override fun onIsVecInRange(vec: Vec3, current: Boolean): Boolean {
        if (current) return true
        val level = env.world
        val castVec = getCasterPosition() ?: Vec3.ZERO
        val casterShip = level.getLoadedShipManagingPos(castVec.toJOML())
        val posShip = level.getLoadedShipManagingPos(vec.toJOML())

        // Is Caster in the Shipyard?
        casterShip?.let { casterShip ->
            // Is Target Position on a Ship?
            posShip?.let { posShip ->
                // Transform Target to Worldspace, then to the Caster's Shipyard
                return env.isVecInRange(casterShip.positionToShip(posShip.positionToWorld(vec)))
            }
            // Transform Target to Caster's Shipyard
            return env.isVecInRange(casterShip.positionToShip(vec))
        }

        // Is Target Position on a Ship?
        // Transform Target to Worldspace
        posShip?.let { ship -> return env.isVecInRange(ship.positionToWorld(vec)) }

        // Neither on a Ship, normal functions
        return env.isVecInRange(vec)
    }

    private fun getCasterPosition(): Vec3? {
        env.castingEntity?.position()?.let { return it }

        if (env is CircleCastEnv)
            return env.impetus?.blockPos?.center

        // Apparently we have nothing in common to check if a mod is loaded or not so...
        try {
            Class.forName("net.walksanator.hextweaks.casting.environment.ComputerCastingEnv")
            HexTweaksCompat.getComputerPosition(env)?.let { return it }
        } catch (ignored: ClassNotFoundException) {}

        return null
    }
}

class Key(val id: Int) : Key<AmbitRemapping> {}

private object Keygen {
    val rand = Random(2819038190)
    fun randid() = rand.nextInt()
}
