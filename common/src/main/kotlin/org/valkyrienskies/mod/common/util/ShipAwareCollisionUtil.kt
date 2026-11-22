package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import org.joml.primitives.AABBd
import org.valkyrienskies.mod.common.getShipsIntersecting

// Vanilla noCollision reads world chunks only. MixinLevel adds this: project the AABB through each intersecting ship's
// worldToShip and run vanilla's noCollision in shipyard space (not Level.noCollision, which would come back here).
object ShipAwareCollisionUtil {

    private val VS_SCRATCH: ThreadLocal<AABBd> = ThreadLocal.withInitial { AABBd() }

    /** Ship part of Level.noCollision. */
    @JvmStatic
    fun noShipCollision(level: Level, entity: Entity?, worldAabb: AABB): Boolean {
        val worldAabbJoml = worldAabb.toJOML()
        for (ship in level.getShipsIntersecting(worldAabbJoml)) {
            val shipLocalJoml = VS_SCRATCH.get().set(worldAabbJoml).transform(ship.transform.worldToShip)
            if (!(level as VanillaLevelDefaults).`vs$vanillaNoCollision`(entity, shipLocalJoml.toMinecraft())) return false
        }
        return true
    }
}
