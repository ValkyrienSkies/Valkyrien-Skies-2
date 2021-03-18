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

    /**
     * @return [movement] modified such that the entity collides with ships.
     */
    fun adjustEntityMovementForShipCollisions(
        entity: Entity?,
        movement: Vec3d,
        entityBoundingBox: Box,
        world: World
    ): Vec3d {
        val collidingShipPolygons =
            getShipPolygonsCollidingWithEntity(entity, movement, entityBoundingBox.expand(1.0), world)
        // If the entity isn't colliding with any ship polygons, then don't adjust its movement
        if (collidingShipPolygons.isEmpty()) {
            return movement
        }
        val stepHeight: Double = entity?.stepHeight?.toDouble() ?: 0.0
        return adjustEntityMovementForPolygonCollisions(
            movement.toJOML(), entityBoundingBox.toJOML(), stepHeight, collidingShipPolygons
        ).toVec3d()
    }

    /**
     * @return [movement] modified such that the entity is colliding with [collidingPolygons]
     */
    private fun adjustEntityMovementForPolygonCollisions(
        movement: Vector3dc,
        entityBoundingBox: AABBdc,
        entityStepHeight: Double,
        collidingPolygons: List<ConvexPolygonc>
    ): Vector3dc {
        val originalMovement: Vector3dc = movement

        // Compute the collision response assuming the entity can't step
        val collisionResponseAssumingNoStep =
            adjustMovementComponentWise(entityBoundingBox, originalMovement, collidingPolygons)

        // If [entityStepHeight] is 0 then it can't step
        if (entityStepHeight == 0.0) {
            return collisionResponseAssumingNoStep
        }

        // If the entity is not moving horizontally then it can't step
        if (movement.x() * movement.x() + movement.z() * movement.z() < 1e-4) {
            return collisionResponseAssumingNoStep
        }

        // Determine if the entity is standing on the polygons
        val isEntityStandingOnPolygons =
            isEntityStandingOnPolygons(
                createFromAABB(entityBoundingBox, null), originalMovement, collidingPolygons
            )

        // If the entity is not standing on the polygons then it can't step
        if (!isEntityStandingOnPolygons) {
            return collisionResponseAssumingNoStep
        }

        // Compute the collision response assuming the entity can step
        val collisionResponseAssumingFullStep =
            adjustMovementComponentWise(
                entityBoundingBox,
                Vector3d(originalMovement.x(), entityStepHeight, originalMovement.z()),
                collidingPolygons
            )

        val originalMovementSpeedSq =
            originalMovement.x() * originalMovement.x() + originalMovement.z() * originalMovement.z()
        val collisionResponseAssumingNoStepHorizontalSpeedSq =
            collisionResponseAssumingNoStep.x() * collisionResponseAssumingNoStep.x() + collisionResponseAssumingNoStep.z() * collisionResponseAssumingNoStep.z()
        val collisionResponseAssumingFullStepHorizontalSpeedSq =
            collisionResponseAssumingFullStep.x() * collisionResponseAssumingFullStep.x() + collisionResponseAssumingFullStep.z() * collisionResponseAssumingFullStep.z()

        // Only choose [collisionResponseAssumingFullStep] if it has a larger horizontal speed than [collisionResponseAssumingNoStep]
        if (collisionResponseAssumingFullStepHorizontalSpeedSq >= collisionResponseAssumingNoStepHorizontalSpeedSq && collisionResponseAssumingFullStepHorizontalSpeedSq >= originalMovementSpeedSq) {
            // Now that we've chosen [collisionResponseAssumingFullStep], move the entity downwards such that it is still on the ground
            val entityAfterSteppingFullyPolygon: ConvexPolygonc =
                createFromAABB(
                    entityBoundingBox.translate(
                        collisionResponseAssumingFullStep.x(), collisionResponseAssumingFullStep.y(),
                        collisionResponseAssumingFullStep.z(), AABBd()
                    ), null
                )
            val fixStepUpResponse = adjustMovement(
                entityAfterSteppingFullyPolygon,
                Vector3d(0.0, originalMovement.y() - collisionResponseAssumingFullStep.y(), 0.0),
                collidingPolygons, true, UNIT_NORMALS[1]
            )

            return fixStepUpResponse.add(collisionResponseAssumingFullStep, Vector3d())
        } else {
            // [collisionResponseAssumingNoStep] had a larger horizontal speed, so we choose it instead of [collisionResponseAssumingFullStep]
            return collisionResponseAssumingNoStep
        }
    }

    /**
     * @return [entityVelocity] modified such that the entity is colliding with [collidingPolygons], with the Y-axis prioritized
     */
    private fun adjustMovementComponentWise(
        entityBoundingBox: AABBdc, entityVelocity: Vector3dc, collidingPolygons: List<ConvexPolygonc>
    ): Vector3dc {
        val entityPolygon: ConvexPolygonc = createFromAABB(entityBoundingBox, null)

        // First collide along the y-axis
        val yOnlyResponse = adjustMovement(
            entityPolygon, Vector3d(0.0, entityVelocity.y(), 0.0), collidingPolygons, true, UNIT_NORMALS[1]
        )

        entityPolygon.points.forEach {
            it as Vector3d
            it.add(yOnlyResponse)
        }

        // Then collide along the x-axis
        val xOnlyResponse = adjustMovement(
            entityPolygon, Vector3d(entityVelocity.x(), 0.0, 0.0), collidingPolygons, true,
            UNIT_NORMALS[0]
        )

        entityPolygon.points.forEach {
            it as Vector3d
            it.add(xOnlyResponse)
        }

        // Finally collide along the z-axis
        val zOnlyResponse =
            adjustMovement(
                entityPolygon, Vector3d(0.0, 0.0, entityVelocity.z()), collidingPolygons,
                true, UNIT_NORMALS[2]
            )

        return Vector3d(xOnlyResponse.x(), yOnlyResponse.y(), zOnlyResponse.z())
    }

    /**
     * @return True if and only if [entityPolygon] is standing on [collidingPolygons].
     */
    private fun isEntityStandingOnPolygons(
        entityPolygon: ConvexPolygonc, entityVelocity: Vector3dc, collidingPolygons: List<ConvexPolygonc>
    ): Boolean {
        val collisionResult = CollisionResult.create()

        // region declare temp objects
        val temp1 = CollisionRange.create()
        val temp2 = CollisionRange.create()
        val temp3 = Vector3d()
        // endregion

        for (shipPolygon in collidingPolygons) {
            val normals: MutableList<Vector3dc> = ArrayList()

            for (normal in UNIT_NORMALS) normals.add(normal)
            for (normal in shipPolygon.normals) normals.add(normal)

            checkIfColliding(
                entityPolygon,
                shipPolygon,
                entityVelocity,
                normals.iterator(),
                collisionResult,
                temp1,
                temp2
            )
            if (collisionResult.colliding) {
                // Compute the response that pushes the player out of this polygon
                val collisionResponse: Vector3dc = collisionResult.getCollisionResponse(temp3)

                if (Math.toDegrees(collisionResponse.angle(UNIT_NORMALS[1])) < 30) {
                    // If this response is less than 30 degrees from the Y normal then this entity is standing on [collidingPolygons]
                    return true
                }
            }
        }
        return false
    }

    /**
     * @return [entityVelocity] modified such that the entity is colliding with [collidingPolygons]. If [forcedResponseNormal] != null then the collision response will be parallel to [forcedResponseNormal].
     */
    private fun adjustMovement(
        entityPolygon: ConvexPolygonc, entityVelocity: Vector3dc, collidingPolygons: List<ConvexPolygonc>,
        forceReduceVelocity: Boolean,
        forcedResponseNormal: Vector3dc? = null
    ): Vector3dc {
        val newEntityVelocity = Vector3d(entityVelocity)
        val collisionResult = CollisionResult.create()

        val originalVelX = entityVelocity.x()
        val originalVelY = entityVelocity.y()
        val originalVelZ = entityVelocity.z()

        // Higher values make polygons push entities out more
        val velocityChangeTolerance = .1

        // region declare temp objects
        val temp1 = CollisionRange.create()
        val temp2 = CollisionRange.create()
        val temp3 = Vector3d()
        // endregion

        for (shipPolygon in collidingPolygons) {
            val normals: MutableList<Vector3dc> = ArrayList()

            for (normal in UNIT_NORMALS) normals.add(normal)
            for (normal in shipPolygon.normals) normals.add(normal)

            checkIfColliding(
                entityPolygon,
                shipPolygon,
                newEntityVelocity,
                normals.iterator(),
                collisionResult,
                temp1,
                temp2
            )
            if (collisionResult.colliding) {
                // Compute the response that pushes the entity out of this polygon
                val collisionResponse: Vector3dc =
                    if (forcedResponseNormal != null) {
                        checkIfColliding(
                            entityPolygon,
                            shipPolygon,
                            newEntityVelocity,
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
                    newEntityVelocity.add(collisionResponse)
                } else {
                    val collisionNormal = collisionResult.collisionAxis

                    // The velocity of the player along [collisionNormal], assuming we add [collisionResponse]
                    val netVelocityAlongNormal = collisionNormal.dot(
                        newEntityVelocity.x() + collisionResponse.x(), newEntityVelocity.y() + collisionResponse.y(),
                        newEntityVelocity.z() + collisionResponse.z()
                    )

                    // The original velocity of the player along [collisionNormal]
                    val originalVelocityAlongNormal = collisionNormal.dot(originalVelX, originalVelY, originalVelZ)

                    if (originalVelocityAlongNormal < 0) {
                        if (netVelocityAlongNormal < velocityChangeTolerance && netVelocityAlongNormal > originalVelocityAlongNormal - velocityChangeTolerance) {
                            newEntityVelocity.add(collisionResponse)
                        }
                    } else {
                        if (netVelocityAlongNormal > -velocityChangeTolerance && netVelocityAlongNormal < originalVelocityAlongNormal + velocityChangeTolerance) {
                            newEntityVelocity.add(collisionResponse)
                        }
                    }
                }
            }
        }
        return newEntityVelocity
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
