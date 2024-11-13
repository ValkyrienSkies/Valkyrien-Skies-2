package org.valkyrienskies.mod.api

import org.joml.Quaterniondc
import org.joml.primitives.AABBdc

/**
 * Give an entity an oriented box to use for collisions rather than the default Minecraft AABB.
 *
 * You can either
 * - Implement this on the entity itself, or
 * - Register an implementation for a particular entity
 */
interface VsCustomCollisionEntityOrientedBox {

    /**
     * The box to use for collisions
     */
    val box: AABBdc

    /**
     * The rotation of the bounding box about its center
     */
    val transform: Quaterniondc

}
