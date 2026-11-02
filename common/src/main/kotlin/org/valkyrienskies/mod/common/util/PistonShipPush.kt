package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.Intersectiond
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.isBlockInShipyard
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.transformAabbToWorldObb

object PistonShipPush {
    private const val PUSH_VELOCITY_PER_TICK = 0.51
    private const val TICKS_PER_SECOND = 20.0

    // effectiveMass = mass · V_REF / (V_REF + volume). Small ships push near-vanilla; bigger ships
    // see diminishing returns on the mass factor so a piston can't slam huge ships to piston speed.
    private const val VOLUME_HALF_BLOCKS = 1000.0

    // Skin in front of the piston face we consider "in contact." Tight enough that ships floating
    // in front aren't pushed; loose enough to absorb worldAABB rounding when a ship rests on the face.
    private const val CONTACT_SKIN = 0.05

    // Slime blocks hit harder than a regular pushed block — biasing their share of the per-target
    // Δv budget upward lets a slime-tipped piston exceed pistonPushSpeed slightly.
    private const val SLIME_PUNCH_MULT = 2.0

    // Reused world-axis unit vectors passed as the AABB-target axes to Intersectiond.testObOb.
    private val WORLD_X: Vector3d = Vector3d(1.0, 0.0, 0.0)
    private val WORLD_Y: Vector3d = Vector3d(0.0, 1.0, 0.0)
    private val WORLD_Z: Vector3d = Vector3d(0.0, 0.0, 1.0)

    private data class PistonIntent(
        val dimensionId: DimensionId,
        val targetShipId: Long,
        val sourceShipId: Long?,
        val forceDirWorld: Vector3dc,
        val targetContactWorld: Vector3dc,
        val sourceContactWorld: Vector3dc,
        val cycleScale: Double,
        val punchMult: Double,
    )

    private val pendingIntents: HashMap<Long, MutableList<PistonIntent>> = HashMap()

    @JvmStatic
    fun applyPushForces(level: ServerLevel, pistonPos: BlockPos, be: PistonMovingBlockEntity) {
        val progress = be.getProgress(1.0f)
        if (progress >= 1.0f) return
        // Skip only the non-sticky source piston BE (head retraction with nothing to pull).
        // Pulled-block BEs (non-source-piston during retraction) only exist during sticky pull, so they
        // always represent a pull contact along the slime chain and should be processed.
        if (!be.isExtending && be.isSourcePiston && !isStickyRetraction(be)) return
        // Taper extension force as the piston extends (the rod loses "punch" near full reach). Pull stays
        // full strength on every tick — sticky adhesion doesn't weaken with the head's retraction.
        val cycleScale = if (be.isExtending) (1.0 - progress).toDouble() else 1.0
        val punchMult = if (be.movedState.`is`(Blocks.SLIME_BLOCK)) SLIME_PUNCH_MULT else 1.0

        val outwardDir: Direction = be.direction
        val forceDir: Direction = be.movementDirection
        val contactBlock = if (be.isExtending) pistonPos else pistonPos.relative(outwardDir)

        val sourceShip: ServerShip? = level.getShipManagingPos(pistonPos)

        val sweptShipLocal = AABB(contactBlock).expandTowards(
            outwardDir.stepX * CONTACT_SKIN,
            outwardDir.stepY * CONTACT_SKIN,
            outwardDir.stepZ * CONTACT_SKIN
        )

        val forceDirLocal = Vector3d(forceDir.stepX.toDouble(), forceDir.stepY.toDouble(), forceDir.stepZ.toDouble())
        // On extension forceDir == outwardDir == +D; only retraction needs a separate outward vector.
        val outwardDirLocal = if (be.isExtending) forceDirLocal
            else Vector3d(outwardDir.stepX.toDouble(), outwardDir.stepY.toDouble(), outwardDir.stepZ.toDouble())
        val contactCenterLocal = Vector3d(contactBlock.x + 0.5, contactBlock.y + 0.5, contactBlock.z + 0.5)

        val sweptWorld: AABB
        val forceDirWorld: Vector3d
        val outwardDirWorld: Vector3d
        val contactCenterWorld: Vector3d
        if (sourceShip != null) {
            sweptWorld = sweptShipLocal.toJOML().transform(sourceShip.shipToWorld).toMinecraft()
            forceDirWorld = sourceShip.shipToWorld.transformDirection(Vector3d(forceDirLocal))
            outwardDirWorld = sourceShip.shipToWorld.transformDirection(Vector3d(outwardDirLocal))
            contactCenterWorld = sourceShip.shipToWorld.transformPosition(Vector3d(contactCenterLocal))
        } else {
            sweptWorld = sweptShipLocal
            forceDirWorld = forceDirLocal
            outwardDirWorld = outwardDirLocal
            contactCenterWorld = contactCenterLocal
        }

        val sourceShipId: Long? = sourceShip?.id

        // Narrow-phase: decompose our sweep AABB into a world-space OBB and SAT-test each broad-phase
        // candidate against it. Rejects ships that intersect the loose AABB only because the source
        // ship's rotation inflated it.
        val obbCenter = Vector3d()
        val obbAxisX = Vector3d()
        val obbAxisY = Vector3d()
        val obbAxisZ = Vector3d()
        val obbHE = Vector3d()
        level.transformAabbToWorldObb(sweptShipLocal.toJOML(), obbCenter, obbAxisX, obbAxisY, obbAxisZ, obbHE)
        val targetCenter = Vector3d()
        val targetHE = Vector3d()

        for (target in level.getShipsIntersecting(sweptWorld)) {
            if (target.id == sourceShipId) continue
            if (target !is ServerShip) continue

            val tAabb = target.worldAABB
            targetCenter.set(
                (tAabb.minX() + tAabb.maxX()) * 0.5,
                (tAabb.minY() + tAabb.maxY()) * 0.5,
                (tAabb.minZ() + tAabb.maxZ()) * 0.5,
            )
            targetHE.set(
                (tAabb.maxX() - tAabb.minX()) * 0.5,
                (tAabb.maxY() - tAabb.minY()) * 0.5,
                (tAabb.maxZ() - tAabb.minZ()) * 0.5,
            )
            if (!Intersectiond.testObOb(obbCenter, obbAxisX, obbAxisY, obbAxisZ, obbHE,
                    targetCenter, WORLD_X, WORLD_Y, WORLD_Z, targetHE)) continue

            val contactWorld = clampPointIntoAabb(contactCenterWorld, target.worldAABB)
            pendingIntents.getOrPut(target.id) { mutableListOf() }.add(
                PistonIntent(level.dimensionId, target.id, sourceShipId, Vector3d(forceDirWorld), contactWorld, contactCenterWorld, cycleScale, punchMult)
            )
        }

        if (sourceShip != null && isPushingWorld(level, contactCenterWorld, outwardDirWorld)) {
            pendingIntents.getOrPut(sourceShipId!!) { mutableListOf() }.add(
                PistonIntent(level.dimensionId, sourceShipId, sourceShipId, Vector3d(forceDirWorld).negate(), contactCenterWorld, contactCenterWorld, cycleScale, punchMult)
            )
        }
    }

    @JvmStatic
    fun flushPending() {
        if (pendingIntents.isEmpty()) return
        val server = ValkyrienSkiesMod.currentServer ?: run { pendingIntents.clear(); return }
        val shipWorld = server.shipObjectWorld

        for ((targetShipId, intents) in pendingIntents) {
            val target = shipWorld.allShips.getById(targetShipId) as? ServerShip ?: continue

            val mass = target.inertiaData.mass
            if (!mass.isFinite() || mass <= 0.0) continue
            val effectiveMass = mass * VOLUME_HALF_BLOCKS / (VOLUME_HALF_BLOCKS + bbVolume(target))

            // Distribute the per-tick Δv budget across intents weighted by punchMult: normal intents
            // share evenly, but slime intents take a larger share — letting them push the ship past
            // pistonPushSpeed by the fraction (mean punchMult > 1).
            val cap = VSGameConfig.SERVER.pistonPushSpeed
            val perIntentScale = 1.0 / intents.size

            for (intent in intents) {
                val pointVel = pointVelocityAlongAxis(target, intent.targetContactWorld, intent.forceDirWorld)
                val deltaV = (cap - pointVel) * perIntentScale * intent.punchMult
                if (deltaV <= 0.0) continue

                val adapter = ValkyrienSkiesMod.getOrCreateGTPA(intent.dimensionId)
                val force = Vector3d(intent.forceDirWorld).mul(effectiveMass * deltaV * TICKS_PER_SECOND * intent.cycleScale)
                adapter.applyWorldForce(intent.targetShipId, force, intent.targetContactWorld)

                if (intent.sourceShipId != null && intent.sourceShipId != intent.targetShipId) {
                    val recoil = Vector3d(force).negate()
                    adapter.applyWorldForce(intent.sourceShipId, recoil, intent.sourceContactWorld)
                }
            }
        }
        pendingIntents.clear()
    }

    private fun isStickyRetraction(be: PistonMovingBlockEntity): Boolean =
        be.movedState.`is`(Blocks.STICKY_PISTON)

    private fun isPushingWorld(level: ServerLevel, pistonHeadWorld: Vector3dc, outwardDirWorld: Vector3dc): Boolean {
        if (isWorldSolidAt(level, pistonHeadWorld.x(), pistonHeadWorld.y(), pistonHeadWorld.z())) return true
        val aheadX = pistonHeadWorld.x() + outwardDirWorld.x()
        val aheadY = pistonHeadWorld.y() + outwardDirWorld.y()
        val aheadZ = pistonHeadWorld.z() + outwardDirWorld.z()
        return isWorldSolidAt(level, aheadX, aheadY, aheadZ)
    }

    private fun isWorldSolidAt(level: ServerLevel, x: Double, y: Double, z: Double): Boolean {
        val pos = BlockPos.containing(x, y, z)
        if (level.isBlockInShipyard(pos)) return false
        val state = level.getBlockState(pos)
        return !state.isAir && !state.getCollisionShape(level, pos).isEmpty
    }

    private fun bbVolume(ship: ServerShip): Double {
        val bb = ship.shipAABB ?: return 1.0
        val dx = (bb.maxX() - bb.minX() + 1).toDouble()
        val dy = (bb.maxY() - bb.minY() + 1).toDouble()
        val dz = (bb.maxZ() - bb.minZ() + 1).toDouble()
        return dx * dy * dz
    }

    private fun pointVelocityAlongAxis(target: ServerShip, pointWorld: Vector3dc, axisWorld: Vector3dc): Double {
        val comWorld = target.shipToWorld.transformPosition(Vector3d(target.inertiaData.centerOfMass))
        val r = Vector3d(pointWorld).sub(comWorld)
        val omega = target.angularVelocity
        val rotational = Vector3d(omega).cross(r)
        val velAtPoint = Vector3d(target.velocity).add(rotational)
        return velAtPoint.dot(axisWorld)
    }

    private fun clampPointIntoAabb(point: Vector3dc, aabb: org.joml.primitives.AABBdc): Vector3d {
        return Vector3d(
            point.x().coerceIn(aabb.minX(), aabb.maxX()),
            point.y().coerceIn(aabb.minY(), aabb.maxY()),
            point.z().coerceIn(aabb.minZ(), aabb.maxZ())
        )
    }
}
