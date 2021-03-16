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
        val collidingPolygons =
            getShipPolygonsCollidingWithEntity(entity, movement, entityBoundingBox.expand(1.0), world)
        if (collidingPolygons.isNotEmpty()) {
            val newMovement = movement.toJOML()
            val entityPolygon: ConvexPolygonc = createFromAABB(entityBoundingBox.toJOML(), null)

            val yOnlyResponse = adjustMovement(entityPolygon, newMovement, collidingPolygons, true, UNIT_NORMALS[1])

            val idk = adjustMovement(entityPolygon, yOnlyResponse, collidingPolygons, true)

            return idk.toVec3d()
        }
        return movement
    }

    private fun adjustMovement(
        playerPolygon: ConvexPolygonc, playerVelocity: Vector3dc, polygons: List<ConvexPolygonc>,
        forceReduceVelocity: Boolean,
        forcedResponseNormal: Vector3dc? = null
    ): Vector3dc {
        val newMovement = Vector3d(playerVelocity)
        val entityPolygon = playerPolygon
        val collisionResult = CollisionResult.create()

        val originalVelX = playerVelocity.x()
        val originalVelY = playerVelocity.y()
        val originalVelZ = playerVelocity.z()

        // region declare temp objects
        val temp1 = CollisionRange.create()
        val temp2 = CollisionRange.create()
        val temp3 = Vector3d()
        val temp4 = Vector3d()
        // endregion
        for (shipPolygon in polygons) {
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
                val collisionResponse: Vector3dc

                if (forcedResponseNormal != null) {
                    checkIfColliding(
                        entityPolygon,
                        shipPolygon,
                        newMovement,
                        normals.iterator(),
                        collisionResult,
                        temp1,
                        temp2,
                        forcedResponseNormal
                    )
                    collisionResponse = collisionResult.getCollisionResponse(temp3)
                } else {
                    collisionResponse = collisionResult.getCollisionResponse(temp4)
                }

                if (!forceReduceVelocity) {
                    newMovement.add(collisionResponse)
                } else {
                    if (originalVelY < 0) {
                        if (newMovement.y() + collisionResponse.y() < .1 && newMovement.y() + collisionResponse.y() > originalVelY - .1) {
                            newMovement.y += collisionResponse.y()
                        }
                    } else {
                        if (newMovement.y() + collisionResponse.y() > -.1 && newMovement.y() + collisionResponse.y() < originalVelY + .1) {
                            newMovement.y += collisionResponse.y()
                        }
                    }

                    if (originalVelX < 0) {
                        if (newMovement.x() + collisionResponse.x() < .1 && newMovement.x() + collisionResponse.x() > originalVelX - .1) {
                            newMovement.x += collisionResponse.x()
                        }
                    } else {
                        if (newMovement.x() + collisionResponse.x() > -.1 && newMovement.x() + collisionResponse.x() < originalVelX + .1) {
                            newMovement.x += collisionResponse.x()
                        }
                    }

                    if (originalVelZ < 0) {
                        if (newMovement.z() + collisionResponse.z() < .1 && newMovement.z() + collisionResponse.z() > originalVelZ - .1) {
                            newMovement.z += collisionResponse.z()
                        }
                    } else {
                        if (newMovement.z() + collisionResponse.z() > -.1 && newMovement.z() + collisionResponse.z() < originalVelZ + .1) {
                            newMovement.z += collisionResponse.z()
                        }
                    }
                }
            }
        }
        return newMovement
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
