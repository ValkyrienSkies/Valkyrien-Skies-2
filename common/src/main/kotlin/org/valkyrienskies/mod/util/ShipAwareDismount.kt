package org.valkyrienskies.mod.util

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.vehicle.DismountHelper
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship

// Vanilla mounts (AbstractHorse, Pig, Boat, Strider, AbstractMinecart) build candidate dismount positions in WORLD frame and validate via DismountHelper. For a mount on a ship the world cells beside it are air (the deck lives in shipyard coords) so the search always fails and the rider falls into the mount's hitbox. Caller must seed the candidates' Y with the mount's foot Y (e.g. mount.boundingBox.minY) so the per-shipyard probe begins at deck level.
object ShipAwareDismount {

    private val VS_IN_FWD: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val VS_OUT_FWD: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val VS_IN_BACK: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val VS_OUT_BACK: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }

    @JvmStatic
    fun tryShipyardSpots(
        level: Level,
        passenger: LivingEntity,
        ship: Ship,
        worldCandidates: Iterable<Vec3>,
        maxYStepsUp: Int
    ): Vec3? {
        val w2s = ship.transform.worldToShip
        val s2w = ship.transform.shipToWorld
        for (worldCandidate in worldCandidates) {
            val shipyard = w2s.transformPosition(
                VS_IN_FWD.get().set(worldCandidate.x, worldCandidate.y, worldCandidate.z),
                VS_OUT_FWD.get()
            )
            val sx = Math.floor(shipyard.x).toInt()
            val sz = Math.floor(shipyard.z).toInt()
            val sy0 = Math.floor(shipyard.y).toInt()
            for (pose in passenger.dismountPoses) {
                val poseBox = passenger.getLocalBoundsForPose(pose)
                for (dy in 0..maxYStepsUp) {
                    val probePos = BlockPos(sx, sy0 + dy, sz)
                    val floorH = level.getBlockFloorHeight(probePos)
                    if (!DismountHelper.isBlockFloorValid(floorH)) continue
                    val shipyardSpot = Vec3(shipyard.x, probePos.y + floorH, shipyard.z)
                    if (!DismountHelper.canDismountTo(level, passenger, poseBox.move(shipyardSpot))) continue
                    passenger.pose = pose
                    val worldSpot = s2w.transformPosition(
                        VS_IN_BACK.get().set(shipyardSpot.x, shipyardSpot.y, shipyardSpot.z),
                        VS_OUT_BACK.get()
                    )
                    return Vec3(worldSpot.x, worldSpot.y, worldSpot.z)
                }
            }
        }
        return null
    }
}
