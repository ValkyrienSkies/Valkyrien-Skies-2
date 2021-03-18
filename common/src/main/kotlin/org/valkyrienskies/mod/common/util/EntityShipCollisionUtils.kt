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

        // If the entity isn't colliding with any ship polygons, then don't adjust its movement
        if (collidingPolygons.isEmpty()) {
            return movement
        }

        val originalMovement: Vector3dc = movement.toJOML()

        // Compute the collision response assuming the entity can't step
        val collisionResponseAssumingNoStep =
            adjustMovementComponentWise(entityBoundingBox.toJOML(), originalMovement, collidingPolygons)

        // If the entity is null then it cannot step
        if (entity == null) {
            return collisionResponseAssumingNoStep.toVec3d()
        }

        // If the entity is not moving horizontally then it cannot step
        if (movement.x * movement.x + movement.z * movement.z < 1e-4) {
            return collisionResponseAssumingNoStep.toVec3d()
        }

        // Determine if the entity is standing on the polygons
        val isPlayerStandingOnPolygons =
            canPlayerStand(createFromAABB(entityBoundingBox.toJOML(), null), originalMovement, collidingPolygons)

        // If the entity is not standing on the polygons then it cannot step
        if (!isPlayerStandingOnPolygons) {
            return collisionResponseAssumingNoStep.toVec3d()
        }

        // Compute the collision response assuming the entity can step
        val collisionResponseAssumingFullStep =
            adjustMovementComponentWise(
                entityBoundingBox.toJOML(),
                Vector3d(originalMovement.x(), entity.stepHeight.toDouble(), originalMovement.z()),
                collidingPolygons
            )

        val collisionResponseAssumingNoStepHorizontalSpeedSq =
            collisionResponseAssumingNoStep.x() * collisionResponseAssumingNoStep.x() + collisionResponseAssumingNoStep.z() * collisionResponseAssumingNoStep.z()
        val collisionResponseAssumingFullStepHorizontalSpeedSq =
            collisionResponseAssumingFullStep.x() * collisionResponseAssumingFullStep.x() + collisionResponseAssumingFullStep.z() * collisionResponseAssumingFullStep.z()

        // Only choose [collisionResponseAssumingFullStep] if it has a larger horizontal speed than [collisionResponseAssumingNoStep]
        if (collisionResponseAssumingFullStepHorizontalSpeedSq >= collisionResponseAssumingNoStepHorizontalSpeedSq) {
            // Now that we've chosen [collisionResponseAssumingFullStep], move the entity downwards such that it is still on the ground
            val entityAfterSteppingFullyPolygon: ConvexPolygonc =
                createFromAABB(
                    entityBoundingBox.offset(
                        collisionResponseAssumingFullStep.x(), collisionResponseAssumingFullStep.y(),
                        collisionResponseAssumingFullStep.z()
                    ).toJOML(), null
                )
            val fixStepUpResponse = adjustMovement(
                entityAfterSteppingFullyPolygon,
                Vector3d(0.0, originalMovement.y() - collisionResponseAssumingFullStep.y(), 0.0),
                collidingPolygons, true, UNIT_NORMALS[1]
            )

            val collisionResponseAssumingPartialStep =
                fixStepUpResponse.add(collisionResponseAssumingFullStep, Vector3d())

            return collisionResponseAssumingPartialStep.toVec3d()
        } else {
            // [collisionResponseAssumingNoStep] had a larger horizontal speed, so we choose it instead of [collisionResponseAssumingFullStep]
            return collisionResponseAssumingNoStep.toVec3d()
        }
    }

    private fun adjustMovementComponentWise(
        playerAABB: AABBdc, playerVelocity: Vector3dc, collidingPolygons: List<ConvexPolygonc>
    ): Vector3dc {
        val entityPolygon: ConvexPolygonc = createFromAABB(playerAABB, null)

        val yOnlyResponse = adjustMovement(
            entityPolygon, Vector3d(0.0, playerVelocity.y(), 0.0), collidingPolygons, true, UNIT_NORMALS[1]
        )

        entityPolygon.points.forEach {
            it as Vector3d
            it.add(yOnlyResponse)
        }

        val xOnlyResponse = adjustMovement(
            entityPolygon, Vector3d(playerVelocity.x(), 0.0, 0.0), collidingPolygons, true,
            UNIT_NORMALS[0]
        )

        entityPolygon.points.forEach {
            it as Vector3d
            it.add(xOnlyResponse)
        }

        val zOnlyResponse =
            adjustMovement(
                entityPolygon, Vector3d(0.0, 0.0, playerVelocity.z()), collidingPolygons,
                true, UNIT_NORMALS[2]
            )

        return Vector3d(xOnlyResponse.x(), yOnlyResponse.y(), zOnlyResponse.z())
    }

    private fun canPlayerStand(
        playerPolygon: ConvexPolygonc, playerVelocity: Vector3dc, polygons: List<ConvexPolygonc>
    ): Boolean {
        val newMovement = Vector3d(playerVelocity)
        val entityPolygon = playerPolygon
        val collisionResult = CollisionResult.create()

        // region declare temp objects
        val temp1 = CollisionRange.create()
        val temp2 = CollisionRange.create()
        val temp3 = Vector3d()
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
                // Compute the response that pushes the player out of this polygon
                val collisionResponse: Vector3dc = collisionResult.getCollisionResponse(temp3)

                if (Math.toDegrees(collisionResponse.angle(UNIT_NORMALS[1])) < 30) {
                    return true
                }
            }
        }
        return false
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

        // Higher values make polygons push players out more
        val velocityChangeTolerance = .1

        // region declare temp objects
        val temp1 = CollisionRange.create()
        val temp2 = CollisionRange.create()
        val temp3 = Vector3d()
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
                // Compute the response that pushes the player out of this polygon
                val collisionResponse: Vector3dc =
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
                        collisionResult.getCollisionResponse(temp3)
                    } else {
                        collisionResult.getCollisionResponse(temp3)
                    }

                if (!forceReduceVelocity) {
                    newMovement.add(collisionResponse)
                } else {
                    val collisionNormal = collisionResult.collisionAxis

                    // The velocity of the player along [collisionNormal], assuming we add [collisionResponse]
                    val netVelocityAlongNormal = collisionNormal.dot(
                        newMovement.x() + collisionResponse.x(), newMovement.y() + collisionResponse.y(),
                        newMovement.z() + collisionResponse.z()
                    )

                    // The original velocity of the player along [collisionNormal]
                    val originalVelocityAlongNormal = collisionNormal.dot(originalVelX, originalVelY, originalVelZ)

                    if (originalVelocityAlongNormal < 0) {
                        if (netVelocityAlongNormal < velocityChangeTolerance && netVelocityAlongNormal > originalVelocityAlongNormal - velocityChangeTolerance) {
                            newMovement.add(collisionResponse)
                        }
                    } else {
                        if (netVelocityAlongNormal > -velocityChangeTolerance && netVelocityAlongNormal < originalVelocityAlongNormal + velocityChangeTolerance) {
                            newMovement.add(collisionResponse)
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
