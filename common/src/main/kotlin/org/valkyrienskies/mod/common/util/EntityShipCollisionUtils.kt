package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.collision.ConvexPolygonc
import org.valkyrienskies.core.collision.EntityPolygonCollider
import org.valkyrienskies.core.collision.TransformedCuboidPolygon.Companion.createFromAABB
import org.valkyrienskies.core.game.IEntityProvider
import org.valkyrienskies.core.util.extend
import org.valkyrienskies.mod.common.shipObjectWorld

object EntityShipCollisionUtils {

    /**
     * @return [movement] modified such that the entity collides with ships.
     */
    fun adjustEntityMovementForShipCollisions(
        entity: Entity?,
        movement: Vec3,
        entityBoundingBox: AABB,
        world: Level
    ): Vec3 {
        val collidingShipPolygons =
            getShipPolygonsCollidingWithEntity(entity, movement, entityBoundingBox.inflate(1.0), world)
        // If the entity isn't colliding with any ship polygons, then don't adjust its movement
        if (collidingShipPolygons.isEmpty()) {
            return movement
        }
        val stepHeight: Double = entity?.maxUpStep?.toDouble() ?: 0.0
        val (newMovement, shipCollidingWith) = EntityPolygonCollider.adjustEntityMovementForPolygonCollisions(
            movement.toJOML(), entityBoundingBox.toJOML(), stepHeight, collidingShipPolygons
        )
        if (entity != null) {
            if (shipCollidingWith != null) {
                // Update the [IEntity.lastShipStoodOn]
                (entity as IEntityProvider).vsEntity.lastShipStoodOn = shipCollidingWith
            } else if (entity.isOnGround) {
                // Don't drag entities on the ground
                (entity as IEntityProvider).vsEntity.lastShipStoodOn = null
            }
        }
        return newMovement.toVec3d()
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
        for (shipObject in world.shipObjectWorld.getShipObjectsIntersecting(entityBoundingBoxExtended)) {
            val shipTransform = shipObject.shipData.shipTransform
            val entityPolyInShipCoordinates: ConvexPolygonc = createFromAABB(
                entityBoxWithMovement.toJOML(),
                shipTransform.worldToShipMatrix
            )
            val entityBoundingBoxInShipCoordinates: AABBdc = entityPolyInShipCoordinates.getEnclosingAABB(AABBd())
            val shipBlockCollisionStream =
                world.getBlockCollisions(entity, entityBoundingBoxInShipCoordinates.toMinecraft())
            shipBlockCollisionStream.forEach { voxelShape: VoxelShape ->
                voxelShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                    val shipPolygon: ConvexPolygonc = createFromAABB(
                        AABBd(minX, minY, minZ, maxX, maxY, maxZ),
                        shipTransform.shipToWorldMatrix,
                        shipObject.shipData.id
                    )
                    collidingPolygons.add(shipPolygon)
                }
            }
        }
        return collidingPolygons
    }
}
