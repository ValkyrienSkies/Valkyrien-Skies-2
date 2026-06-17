package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import org.joml.primitives.AABBd
import org.valkyrienskies.mod.common.getShipsIntersecting

// Vanilla Level.noCollision reads world chunks only — ship blocks at the same world AABB live in shipyard chunks and aren't seen. Wrap projects the input AABB through each intersecting ship's worldToShip and re-runs noCollision in shipyard space; non-empty in either frame returns false.
object ShipAwareCollisionUtil {

    private val VS_SCRATCH: ThreadLocal<AABBd> = ThreadLocal.withInitial { AABBd() }

    @JvmStatic
    fun noCollisionIncludingShips(level: Level, entity: Entity?, worldAabb: AABB): Boolean {
        if (!level.noCollision(entity, worldAabb)) return false
        val worldAabbJoml = worldAabb.toJOML()
        for (ship in level.getShipsIntersecting(worldAabbJoml)) {
            val shipLocalJoml = VS_SCRATCH.get().set(worldAabbJoml).transform(ship.transform.worldToShip)
            if (!level.noCollision(entity, shipLocalJoml.toMinecraft())) return false
        }
        return true
    }

    @JvmStatic
    fun noCollisionIncludingShips(level: Level, worldAabb: AABB): Boolean {
        if (!level.noCollision(worldAabb)) return false
        val worldAabbJoml = worldAabb.toJOML()
        for (ship in level.getShipsIntersecting(worldAabbJoml)) {
            val shipLocalJoml = VS_SCRATCH.get().set(worldAabbJoml).transform(ship.transform.worldToShip)
            if (!level.noCollision(shipLocalJoml.toMinecraft())) return false
        }
        return true
    }
}
