package org.valkyrienskies.mod.compat.hexcasting

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent.HasEditPermissionsAt
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent.IsVecInRange
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent.Key
import at.petrak.hexcasting.api.casting.eval.env.CircleCastEnv
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.api.getLoadedShipManagingPos
import org.valkyrienskies.mod.api.toContaining
import org.valkyrienskies.mod.api.transformToShip
import org.valkyrienskies.mod.api.transformToWorld
import java.util.UUID

@OptIn(GameTickOnly::class)
open class ShipAmbit(val env: CastingEnvironment) : IsVecInRange, HasEditPermissionsAt {
    private val id = UUID.randomUUID()
    private val key = Key(id)

    override fun getKey(): Key<*> = key

    override fun onHasEditPermissionsAt(pos: BlockPos, current: Boolean): Boolean {
        return current || env.hasEditPermissionsAt(env.world.transformToWorld(pos.center).toContaining())
    }

    override fun onIsVecInRange(vec: Vec3, current: Boolean): Boolean {
        if (current) return true

        val level = env.world
        val castVec = getCasterPosition() ?: Vec3.ZERO
        val casterShip = level.getLoadedShipManagingPos(castVec)
        val otherShip = level.getLoadedShipManagingPos(vec)

        if (casterShip == otherShip) return false

        val otherPos = otherShip.transformToWorld(vec)

        return env.isVecInRange(casterShip.transformToShip(otherPos))
    }

    open fun getCasterPosition(): Vec3? {
        return when (env) {
            is CircleCastEnv -> {
                env.impetus?.blockPos?.center
            }
            else -> {
                env.caster?.position() ?: env.castingEntity?.position()
            }
        }
    }
}

class Key(val id: UUID) : Key<ShipAmbit>
