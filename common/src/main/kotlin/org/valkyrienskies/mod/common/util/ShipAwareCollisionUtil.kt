package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import org.joml.primitives.AABBd
import org.valkyrienskies.mod.common.getShipsIntersecting

// Vanilla noCollision reads world chunks only. MixinLevel adds this: project the AABB through each intersecting ship's
// worldToShip and re-run noCollision in shipyard space.
object ShipAwareCollisionUtil {

    private val VS_SCRATCH: ThreadLocal<AABBd> = ThreadLocal.withInitial { AABBd() }

    /** Ship part of Level.noCollision. Re-enters noCollision with shipyard AABBs, which intersect no ship. */
    @JvmStatic
    fun noShipCollision(level: Level, entity: Entity?, worldAabb: AABB): Boolean {
        val worldAabbJoml = worldAabb.toJOML()
        for (ship in level.getShipsIntersecting(worldAabbJoml)) {
            val shipLocalJoml = VS_SCRATCH.get().set(worldAabbJoml).transform(ship.transform.worldToShip)
            if (!level.noCollision(entity, shipLocalJoml.toMinecraft())) return false
        }
        return true
    }
}
