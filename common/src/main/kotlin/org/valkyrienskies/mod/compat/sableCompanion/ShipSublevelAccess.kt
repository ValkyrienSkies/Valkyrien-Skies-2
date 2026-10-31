package org.valkyrienskies.mod.compat.sableCompanion

import dev.ryanhcode.sable.companion.SubLevelAccess
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import dev.ryanhcode.sable.companion.math.BoundingBox3dc
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.companion.math.Pose3dc
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.bodies.properties.BodyTransform
import org.valkyrienskies.core.api.ships.Ship
import java.util.UUID

class ShipSublevelAccess(val ship: Ship) : SubLevelAccess {
    val UUID = UUID(Long.MAX_VALUE, ship.id)

    override fun logicalPose(): Pose3dc {
        return Pose3d(
            Vector3d(ship.transform.position),
            Quaterniond(ship.transform.rotation),
            Vector3d(ship.transform.positionInModel),
            Vector3d(ship.transform.scaling)
        )
    }

    override fun lastPose(): Pose3dc {
        return Pose3d(
            Vector3d(ship.prevTickTransform.position),
            Quaterniond(ship.prevTickTransform.rotation),
            Vector3d(ship.prevTickTransform.positionInModel),
            Vector3d(ship.prevTickTransform.scaling)
        )
    }

    override fun boundingBox(): BoundingBox3dc {
        val aabb = ship.worldAABB
        return BoundingBox3d(
            aabb.minX(), aabb.minY(), aabb.minZ(),
            aabb.maxX(), aabb.maxY(), aabb.maxZ()
        )
    }

    override fun getUniqueId(): UUID {
        return UUID
    }

    override fun getName(): String? {
        return ship.slug
    }
}
