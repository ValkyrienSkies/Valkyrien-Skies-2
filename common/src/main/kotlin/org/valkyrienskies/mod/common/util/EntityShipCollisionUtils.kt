package org.valkyrienskies.mod.common.util

import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.World
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.collision.CollisionRange
import org.valkyrienskies.core.collision.CollisionResult
import org.valkyrienskies.core.collision.ConvexPolygonc
import org.valkyrienskies.core.collision.SATConvexPolygonCollider.checkIfColliding
import org.valkyrienskies.core.collision.TransformedCuboidPolygon.Companion.createFromAABB
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.ArrayList

object EntityShipCollisionUtils {

    private val UNIT_NORMALS =
        arrayOf<Vector3dc>(Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 1.0))

    fun adjustMovementForCollisionsAndShipCollisions(
        entity: Entity?,
        movement: Vec3d,
        entityBoundingBox: Box,
        world: World
    ): Vec3d {
        val collidingPolygons = getShipPolygonsCollidingWithEntity(entity, movement, entityBoundingBox, world)
        if (collidingPolygons.isNotEmpty()) {
            val newMovement = movement.toJOML()
            val entityPolygon: ConvexPolygonc = createFromAABB(entityBoundingBox.toJOML(), null)
            val collisionResult = CollisionResult.create()

            // region declare temp objects
            val temp1 = CollisionRange.create()
            val temp2 = CollisionRange.create()
            val temp3 = Vector3d()
            val temp4 = Vector3d()
            // endregion
            for (shipPolygon in collidingPolygons) {
                val normals: MutableList<Vector3dc> = ArrayList()

                for (normal in UNIT_NORMALS) normals.add(normal)
                for (normal in shipPolygon.normals) {
                    normals.add(normal)
                    for (unitNormal in UNIT_NORMALS) {
                        val crossProduct: Vector3dc = normal.cross(unitNormal, Vector3d()).normalize()
                        if (crossProduct.lengthSquared() > 1.0e-6) {
                            normals.add(crossProduct)
                        }
                    }
                }

                checkIfColliding(
                    entityPolygon,
                    shipPolygon,
                    newMovement,
                    normals.iterator(),
                    collisionResult,
                    temp1,
                    temp2
                )
                if (collisionResult.colliding) {
                    val collisionResponse: Vector3dc = collisionResult.getCollisionResponse(temp3)

                    checkIfColliding(
                        entityPolygon,
                        shipPolygon,
                        newMovement,
                        normals.iterator(),
                        collisionResult,
                        temp1,
                        temp2,
                        UNIT_NORMALS[1]
                    )

                    val yOnlyResponse: Vector3dc = collisionResult.getCollisionResponse(temp4)

                    if (true) {
                        // Only add the collision to the Y axis
                        newMovement.add(yOnlyResponse)
                    } else {
                        newMovement.add(collisionResponse)
                    }
                }
            }
            return newMovement.toVec3d()
        }
        return movement
    }

    private fun getShipPolygonsCollidingWithEntity(
        entity: Entity?,
        movement: Vec3d,
        entityBoundingBox: Box,
        world: World
    ): List<ConvexPolygonc> {
        val entityBoxWithMovement = entityBoundingBox.stretch(movement)
        val collidingPolygons: MutableList<ConvexPolygonc> = ArrayList()
        for (shipObject in world.shipObjectWorld.shipObjects.values) {
            val shipTransform = shipObject.shipData.shipTransform
            val entityPolyInShipCoordinates: ConvexPolygonc = createFromAABB(
                entityBoxWithMovement.toJOML(),
                shipTransform.worldToShipMatrix
            )
            val enclosingBB: AABBdc = entityPolyInShipCoordinates.getEnclosingAABB(AABBd())
            val enclosingBBAsBox = enclosingBB.toMinecraft()
            val stream2 = world.getBlockCollisions(entity, enclosingBBAsBox)
            val aabbList = ArrayList<Box>()
            stream2.forEach { voxelShape: VoxelShape ->
                voxelShape.forEachBox { minX, minY, minZ, maxX, maxY, maxZ ->
                    aabbList.add(Box(minX, minY, minZ, maxX, maxY, maxZ))
                }
            }
            // mergeAABBList(aabbList)
            aabbList.forEach { box: Box ->
                val shipPolygon: ConvexPolygonc = createFromAABB(
                    box.toJOML(),
                    shipTransform.shipToWorldMatrix
                )
                collidingPolygons.add(shipPolygon)
            }
        }
        return collidingPolygons
    }
}
