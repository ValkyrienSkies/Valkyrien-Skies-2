package org.valkyrienskies.mod.common.util

import org.apache.logging.log4j.LogManager
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.mod.common.config.VSGameConfig
import java.lang.Double.isFinite
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class BuoyancyHandlerAttachment : ShipPhysicsListener {
    val buoyancyData = BuoyancyData()
    val LOGGER = LogManager.getLogger("[Valkyrien Skies] Float factory")

    fun setDisplacedVolume(volume: Double) {
        buoyancyData.displacedVolume = volume
    }

    fun setPocketCenter(x: Double, y: Double, z: Double) {
        buoyancyData.pocketCenterX = x
        buoyancyData.pocketCenterY = y
        buoyancyData.pocketCenterZ = z
        buoyancyData.hasPocketCenter = true
    }

    fun setBuoyancyFluidDensity(density: Double) {
        buoyancyData.buoyancyFluidDensity = density
    }

    fun setBuoyancyFluidViscosity(viscosity: Double) {
        buoyancyData.buoyancyFluidViscosity = viscosity
    }


    class BuoyancyData() {
        var displacedVolume = 0.0
        var hasPocketCenter = false
        var buoyancyFluidDensity = WATER_DENSITY
        var buoyancyFluidViscosity = DEFAULT_FLUID_VISCOSITY

        var pocketCenterX = 0.0
        var pocketCenterY = 0.0
        var pocketCenterZ = 0.0

        val tmpForce = Vector3d()
        val tmpPos = Vector3d()

        var smoothedDisplacedVolume = 0.0
        var hasSmoothedCenter = false

        var smoothedCenterX = 0.0
        var smoothedCenterY = 0.0
        var smoothedCenterZ = 0.0

        var lastAppliedBuoyancyForce = 0.0

        var diagOverlapClampCount = 0L
        var diagForceSlewClampCount = 0L
        var diagLeverClampCount = 0L
        var diagDampingClampCount = 0L
        var diagZeroLiquidOverlapCount = 0L

        companion object {
            const val WATER_DENSITY = 1000.0
            const val GRAVITY_MAGNITUDE = 10.0
            const val MAX_POCKET_BUOYANCY_WEIGHT_MULT = 1.0
            const val DEFAULT_FLUID_VISCOSITY = 1000.0
            const val OVERLAP_EPS = 1.0e-6
            const val SMOOTH_DISPLACED_ALPHA = 0.35
            const val SMOOTH_CENTER_ALPHA = 0.25
            const val MAX_FORCE_SLEW_G_PER_TICK = 0.35
            const val MAX_BALANCED_LEVER_ARM = 32.0
            const val MIN_COM_BLEND = 0.55
            const val MAX_DAMPING_FORCE_MULT = 3.0
        }
    }

    override fun physTick(
        physShip: PhysShip, physLevel: PhysLevel
    ) {
        if (!VSGameConfig.COMMON.enableAirPockets) return
        if (true || !VSGameConfig.SERVER.enablePocketBuoyancy) return

        if (!buoyancyData.hasPocketCenter) {
            buoyancyData.lastAppliedBuoyancyForce = 0.0
            buoyancyData.smoothedDisplacedVolume = 0.0
            buoyancyData.hasSmoothedCenter = false
            return
        }

        val displacedRaw: Double = buoyancyData.displacedVolume
        if (!isFinite(
                displacedRaw
            ) || displacedRaw <= BuoyancyData.OVERLAP_EPS
        ) {
            buoyancyData.smoothedDisplacedVolume *= 0.75
            if (buoyancyData.smoothedDisplacedVolume <= BuoyancyData.OVERLAP_EPS) {
                buoyancyData.smoothedDisplacedVolume = 0.0
            }
            buoyancyData.lastAppliedBuoyancyForce = 0.0
            return
        }

        val overlapRaw = physShip.liquidOverlap
        if (!isFinite(overlapRaw)) {
            val count = ++buoyancyData.diagZeroLiquidOverlapCount
            if (count <= 4L || count % 512L == 0L) {
                LOGGER.info(
                    "Pocket buoyancy suppressed shipId={} because liquidOverlap is non-finite: liquidOverlap={} displacedVolume={} count={}",
                    physShip.id,
                    overlapRaw,
                    displacedRaw,
                    count,
                )
            }
            buoyancyData.lastAppliedBuoyancyForce = 0.0
            return
        }
        val overlap = clamp(overlapRaw, 0.0, 1.0)
        if (abs(overlap - overlapRaw) > 1.0e-9) {
            logClamp("overlap", overlapRaw, overlap)
        }
        if (overlap <= BuoyancyData.OVERLAP_EPS) {
            val count = ++buoyancyData.diagZeroLiquidOverlapCount
            if (count <= 4L || count % 512L == 0L) {
                LOGGER.info(
                    "Pocket buoyancy suppressed shipId={} liquidOverlap={} displacedVolume={} smoothedDisplacedVolume={} fluidDensity={} count={}",
                    physShip.id,
                    overlapRaw,
                    displacedRaw,
                    buoyancyData.smoothedDisplacedVolume,
                    buoyancyData.buoyancyFluidDensity,
                    count,
                )
            }
            buoyancyData.lastAppliedBuoyancyForce = 0.0
            return
        }

        val mass = physShip.mass
        if (!isFinite(
                mass
            ) || mass <= BuoyancyData.OVERLAP_EPS
        ) return

        var density: Double = buoyancyData.buoyancyFluidDensity
        if (!isFinite(density) || density <= 0.0) density =
            BuoyancyData.WATER_DENSITY
        density = max(100.0, min(density, 20000.0))

        buoyancyData.smoothedDisplacedVolume +=
            (max(
                0.0, displacedRaw
            ) - buoyancyData.smoothedDisplacedVolume) * BuoyancyData.SMOOTH_DISPLACED_ALPHA
        val displaced = max(0.0, buoyancyData.smoothedDisplacedVolume)
        if (!isFinite(
                displaced
            ) || displaced <= BuoyancyData.OVERLAP_EPS
        ) {
            buoyancyData.lastAppliedBuoyancyForce = 0.0
            return
        }

        val com = physShip.centerOfMass
        val comX = com.x()
        val comY = com.y()
        val comZ = com.z()
        if (!isFinite(comX) || !isFinite(comY) || !isFinite(
                comZ
            )
        ) return

        val rawPocketX: Double = buoyancyData.pocketCenterX
        val rawPocketY: Double = buoyancyData.pocketCenterY
        val rawPocketZ: Double = buoyancyData.pocketCenterZ
        val pocketX = if (isFinite(rawPocketX)) rawPocketX else comX
        val pocketY = if (isFinite(rawPocketY)) rawPocketY else comY
        val pocketZ = if (isFinite(rawPocketZ)) rawPocketZ else comZ

        if (!buoyancyData.hasSmoothedCenter) {
            buoyancyData.smoothedCenterX = pocketX
            buoyancyData.smoothedCenterY = pocketY
            buoyancyData.smoothedCenterZ = pocketZ
            buoyancyData.hasSmoothedCenter = true
        } else {
            buoyancyData.smoothedCenterX += (pocketX - buoyancyData.smoothedCenterX) * BuoyancyData.SMOOTH_CENTER_ALPHA
            buoyancyData.smoothedCenterY += (pocketY - buoyancyData.smoothedCenterY) * BuoyancyData.SMOOTH_CENTER_ALPHA
            buoyancyData.smoothedCenterZ += (pocketZ - buoyancyData.smoothedCenterZ) * BuoyancyData.SMOOTH_CENTER_ALPHA
        }

        var leverX: Double = buoyancyData.smoothedCenterX - comX
        var leverY: Double = buoyancyData.smoothedCenterY - comY
        var leverZ: Double = buoyancyData.smoothedCenterZ - comZ
        val leverLenSq = leverX * leverX + leverY * leverY + leverZ * leverZ
        if (leverLenSq > BuoyancyData.MAX_BALANCED_LEVER_ARM * BuoyancyData.MAX_BALANCED_LEVER_ARM) {
            val leverLen = sqrt(leverLenSq)
            val scale = BuoyancyData.MAX_BALANCED_LEVER_ARM / leverLen
            leverX *= scale
            leverY *= scale
            leverZ *= scale
            logClamp(
                "leverArm", leverLen, BuoyancyData.MAX_BALANCED_LEVER_ARM
            )
        }

        val clampedLeverLen = sqrt(leverX * leverX + leverY * leverY + leverZ * leverZ)
        val leverRatio =
            clamp(clampedLeverLen / BuoyancyData.MAX_BALANCED_LEVER_ARM, 0.0, 1.0)
        val comBlend = clamp(1.0 - 0.45 * leverRatio, BuoyancyData.MIN_COM_BLEND, 1.0)

        val applyX = comX + leverX * comBlend
        val applyY = comY + leverY * comBlend
        val applyZ = comZ + leverZ * comBlend

        var upwardForceTarget =
            displaced * density * BuoyancyData.GRAVITY_MAGNITUDE * overlap
        if (!isFinite(upwardForceTarget) || upwardForceTarget <= 0.0) {
            buoyancyData.lastAppliedBuoyancyForce = 0.0
            return
        }

        val maxForce =
            mass * BuoyancyData.GRAVITY_MAGNITUDE * BuoyancyData.MAX_POCKET_BUOYANCY_WEIGHT_MULT
        if (isFinite(maxForce) && maxForce > 0.0) {
            val clamped = min(upwardForceTarget, maxForce)
            if (abs(clamped - upwardForceTarget) > 1.0e-9) {
                logClamp("forceSlew", upwardForceTarget, clamped)
            }
            upwardForceTarget = clamped
        }

        val prevForce =
            if (isFinite(
                    buoyancyData.lastAppliedBuoyancyForce
                ) && buoyancyData.lastAppliedBuoyancyForce > 0.0
            ) buoyancyData.lastAppliedBuoyancyForce else 0.0
        val maxDeltaForce =
            mass * BuoyancyData.GRAVITY_MAGNITUDE * BuoyancyData.MAX_FORCE_SLEW_G_PER_TICK
        val minForceThisTick = max(0.0, prevForce - maxDeltaForce)
        val maxForceThisTick = prevForce + maxDeltaForce
        val upwardForce = clamp(upwardForceTarget, minForceThisTick, maxForceThisTick)
        if (abs(upwardForce - upwardForceTarget) > 1.0e-9) {
            logClamp("forceSlew", upwardForceTarget, upwardForce)
        }
        buoyancyData.lastAppliedBuoyancyForce = upwardForce
        if (upwardForce <= BuoyancyData.OVERLAP_EPS) return

        buoyancyData.tmpForce.set(0.0, upwardForce, 0.0)
        buoyancyData.tmpPos.set(applyX, applyY, applyZ)
        physShip.applyWorldForceToModelPos(buoyancyData.tmpForce, buoyancyData.tmpPos)
        physShip.doFluidDrag = true

        // Extra damping for very viscous fluids (e.g. lava) to prevent "bounce"/launch oscillations at depth.
        var viscosity: Double = buoyancyData.buoyancyFluidViscosity
        if (isFinite(
                viscosity
            ) && viscosity > BuoyancyData.DEFAULT_FLUID_VISCOSITY * 1.5
        ) {
            viscosity = max(100.0, min(viscosity, 200000.0))
            val viscosityScale =
                max(0.25, min(viscosity / BuoyancyData.DEFAULT_FLUID_VISCOSITY, 20.0))

            val vel = physShip.velocity
            val baseDamping = 0.35
            val damping = baseDamping * viscosityScale * overlap

            var fx = -vel.x() * mass * damping
            var fy = -vel.y() * mass * damping
            var fz = -vel.z() * mass * damping

            val maxDamp =
                mass * BuoyancyData.GRAVITY_MAGNITUDE * BuoyancyData.MAX_DAMPING_FORCE_MULT
            val dampLenSq = fx * fx + fy * fy + fz * fz
            if (dampLenSq > maxDamp * maxDamp && dampLenSq > 1.0e-12) {
                val rawLen = sqrt(dampLenSq)
                val scale = maxDamp / rawLen
                fx *= scale
                fy *= scale
                fz *= scale
                logClamp("damping", rawLen, maxDamp)
            }

            buoyancyData.tmpForce.set(fx, fy, fz)
            physShip.applyWorldForceToModelPos(buoyancyData.tmpForce, com)
        }
    }

    private fun logClamp(kind: String, raw: Double, clamped: Double) {
        if (!isFinite(raw) || !isFinite(clamped)) return
        if (abs(raw - clamped) <= 1.0e-9) return

        val count: Long
        when (kind) {
            "overlap" -> count = ++ buoyancyData.diagOverlapClampCount
            "leverArm" -> count = ++ buoyancyData.diagLeverClampCount
            "damping" -> count = ++ buoyancyData.diagDampingClampCount
            else -> count = ++ buoyancyData.diagForceSlewClampCount
        }

        if (count <= 4L || count % 512L == 0L) {
            LOGGER.debug(
                "Pocket buoyancy clamp kind={} raw={} clamped={} count={}",
                kind,
                raw,
                clamped,
                count
            )
        }
    }

    private fun clamp(v: Double, min: Double, max: Double): Double {
        return if (v < min) min else min(v, max)
    }
}
