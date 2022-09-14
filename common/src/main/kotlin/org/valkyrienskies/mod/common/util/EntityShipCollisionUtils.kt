package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.collision.ConvexPolygonc
import org.valkyrienskies.core.collision.EntityPolygonCollider
import org.valkyrienskies.core.collision.TransformedCuboidPolygon.Companion.createFromAABB
import org.valkyrienskies.core.util.extend
import org.valkyrienskies.mod.common.shipObjectWorld

object EntityShipCollisionUtils {

    @JvmStatic
    fun isCollidingWithUnloadedShips(entity: Entity): Boolean {
        val shipWorld = entity.level.shipObjectWorld
        return shipWorld.queryableShipData.getShipDataIntersecting(entity.boundingBox.toJOML())
            .all { ship -> shipWorld.shipObjects.containsKey(ship.id) }
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
        // TODO: For now only do collision with the player, re-enable this for all entities eventually
        if (entity !is Player) return movement
        val collidingShipPolygons =
            getShipPolygonsCollidingWithEntity(entity, movement, entityBoundingBox.inflate(1.0), world)

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
