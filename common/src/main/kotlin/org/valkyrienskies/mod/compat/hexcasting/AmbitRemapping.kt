package org.valkyrienskies.mod.compat.hexcasting

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent.IsVecInRange
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent.Key
import at.petrak.hexcasting.api.casting.eval.env.CircleCastEnv
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.api.positionToShip
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.toJOML
import kotlin.random.Random

class AmbitRemapping(private val env: CastingEnvironment) : IsVecInRange {
    private val id = Keygen.randid()
    private val key = Key(id)

    override fun getKey(): Key<*> = key

    @OptIn(GameTickOnly::class)
    override fun onIsVecInRange(vec: Vec3, current: Boolean): Boolean {
        if (current) return true
        val level = env.world
        var castVec = getCasterPosition()
        level.getLoadedShipManagingPos(castVec.toJOML())?.let { ship ->
            return env.isVecInRange(ship.positionToShip(vec))
        }

        env.world.getLoadedShipManagingPos(vec.toJOML())?.let { ship ->
            return env.isVecInRange(ship.toWorldCoordinates(vec))
        }

        return env.isVecInRange(vec)
    }

    private fun getCasterPosition() =
        when(env) {
            is CircleCastEnv -> env.impetus?.blockPos?.center ?: Vec3.ZERO
            is PlayerBasedCastEnv -> env.caster?.position() ?: Vec3.ZERO
            else -> Vec3.ZERO // TODO: Add Extra Hexcasting Addon Compat Here (HexTweaks Computers and Hexal Wisps)
        }
}

class Key(val id: Int) : Key<AmbitRemapping> {}

private object Keygen {
    val rand = Random(2819038190)
    fun randid() = rand.nextInt()
}
