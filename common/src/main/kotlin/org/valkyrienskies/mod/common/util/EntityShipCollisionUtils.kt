package org.valkyrienskies.mod.common.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.impl.api.ServerShipInternal
import org.valkyrienskies.core.impl.collision.ConvexPolygonc
import org.valkyrienskies.core.impl.collision.EntityPolygonCollider
import org.valkyrienskies.core.impl.collision.EntityPolygonCollider.createPolygonFromAABB
import org.valkyrienskies.core.impl.util.extend
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.math.max

object EntityShipCollisionUtils {

    @JvmStatic
    fun isCollidingWithUnloadedShips(entity: Entity): Boolean {
        if (entity.level !is ServerLevel) return false

        val shipWorld = entity.level.shipObjectWorld
        return shipWorld.allShips.getIntersecting(entity.boundingBox.toJOML())
            .all { (it as ServerShipInternal).areVoxelsFullyLoaded() }
            .not()
    }

    /**
     * @return [movement] modified such that the entity collides with ships.
     */
    fun adjustEntityMovementForShipCollisions(
        entity: Entity?,
        movement: Vec3,
        entityBoundingBox: AABB,
        world: Level
    ): Vec3 {
        // Inflate the bounding box more for players than other entities, to give players a better collision result.
        // Note that this increases the cost of doing collision, so we only do it for the players
        val inflation = if (entity is Player) 0.5 else 0.1
        val stepHeight: Double = entity?.maxUpStep?.toDouble() ?: 0.0
        // Add [max(stepHeight - inflation, 0.0)] to search for polygons we might collide with while stepping
        val collidingShipPolygons =
            getShipPolygonsCollidingWithEntity(
                entity, Vec3(movement.x(), movement.y() + max(stepHeight - inflation, 0.0), movement.z()),
                entityBoundingBox.inflate(inflation), world
            )

        if (collidingShipPolygons.isEmpty()) {
            return movement
        }

        val (newMovement, shipCollidingWith) = EntityPolygonCollider.adjustEntityMovementForPolygonCollisions(
            movement.toJOML(), entityBoundingBox.toJOML(), stepHeight, collidingShipPolygons
        )
        if (entity != null) {
            if (shipCollidingWith != null) {
                // Update the [IEntity.lastShipStoodOn]
                (entity as IEntityDraggingInformationProvider).draggingInformation.lastShipStoodOn = shipCollidingWith
            }
        }
        return newMovement.toMinecraft()
    }

    private fun getShipPolygonsCollidingWithEntity(
        entity: Entity?,
        movement: Vec3,
        entityBoundingBox: AABB,
        world: Level
    ): List<ConvexPolygonc> {
        val entityBoxWithMovement = entityBoundingBox.expandTowards(movement)
        val collidingPolygons: MutableList<ConvexPolygonc> = ArrayList()
        val entityBoundingBoxExtended = entityBoundingBox.toJOML().extend(movement.toJOML())
        for (shipObject in world.shipObjectWorld.loadedShips.getIntersecting(entityBoundingBoxExtended)) {
            val shipTransform = shipObject.transform
            val entityPolyInShipCoordinates: ConvexPolygonc = createPolygonFromAABB(
                entityBoxWithMovement.toJOML(),
                shipTransform.worldToShip
            )
            val entityBoundingBoxInShipCoordinates: AABBdc = entityPolyInShipCoordinates.getEnclosingAABB(AABBd())
            val shipBlockCollisionStream =
                world.getBlockCollisions(entity, entityBoundingBoxInShipCoordinates.toMinecraft())
            shipBlockCollisionStream.forEach { voxelShape: VoxelShape ->
                voxelShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                    val shipPolygon: ConvexPolygonc = createPolygonFromAABB(
                        AABBd(minX, minY, minZ, maxX, maxY, maxZ),
                        shipTransform.shipToWorld,
                        shipObject.id
                    )
                    collidingPolygons.add(shipPolygon)
                }
            }
        }
        return collidingPolygons
    }
}
