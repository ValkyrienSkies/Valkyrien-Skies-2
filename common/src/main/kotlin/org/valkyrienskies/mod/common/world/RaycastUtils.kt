package org.valkyrienskies.mod.common.world

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import org.joml.Intersectionf
import org.joml.Vector2d
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.ClientShip
import org.joml.primitives.LineSegmentf
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixinducks.feature.clip_replace.ClipContextDuck
import org.valkyrienskies.mod.util.scale
import java.util.Vector
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate

private val logger = LogManager.getLogger("RaycastUtilsKt")

@JvmOverloads
fun Level.clipIncludeShips(
    ctx: ClipContext, shouldTransformHitPos: Boolean = true, skipShip: ShipId? = null, skipWorld: Boolean = false
): BlockHitResult {
    val ccd = (ctx as ClipContextDuck)
    ccd.rayCastExtraParameter = RayCastExtraParameter(
        shouldTransformHitPos = shouldTransformHitPos,
        skipShip = skipShip,
        skipWorld = skipWorld)
    val ret = this.clip(ctx)
    ccd.reset()
    return ret
}

fun Level.clipIncludeShipsImpl(
    ctx: ClipContext, vanillaClip: Operation<BlockHitResult>): BlockHitResult {
    if (ctx !is ClipContextDuck)
        return vanillaClip.call(ctx);

    val vanillaHit = if(ctx.rayCastExtraParameter.skipWorld) {
        val line = ctx.to.subtract(ctx.from)
        BlockHitResult.miss(ctx.to, Direction.getNearest(line.x, line.y, line.z), BlockPos.containing(ctx.to))
    } else vanillaClip.call(ctx)

    if (shipObjectWorld == null) {
        logger.error(
            "shipObjectWorld was empty for level raytrace, this should not be possible! " +
                "Returning vanilla result."
        )
        return vanillaHit
    }

    var closestHit = vanillaHit
    var closestHitPos = vanillaHit.location
    var closestHitDist = closestHitPos.distanceToSqr(ctx.from)

    val clipAABB: AABBdc = AABBd(ctx.from.toJOML(), ctx.to.toJOML()).correctBounds()
    val clipSegment = LineSegmentf(ctx.from.toVector3f(), ctx.to.toVector3f())

    // Iterate every ship, find do the raycast in ship space,
    // choose the raycast with the lowest distance to the start position.
    for (ship in getShipsIntersecting(clipAABB)) {
        val chopParam = Vector2d()
        // Pad AABB size to increase raycast tolerance
        val expandedAABB = AABBd(ship.worldAABB).expand(1.0)
        val intersectType = expandedAABB.intersectsLineSegment(clipSegment, chopParam);
        if (intersectType == Intersectionf.OUTSIDE) {
            continue
        }
        // Skip skipShip
        if (ship.id == ctx.rayCastExtraParameter.skipShip) {
            continue
        }

        var choppedFrom: Vector3d
        var choppedTo : Vector3d
        if (intersectType == Intersectionf.TWO_INTERSECTION) {
            choppedFrom = ctx.from.toJOML().add(ctx.to.toJOML().sub(ctx.from.toJOML()).mul(chopParam.x))
            choppedTo = ctx.from.toJOML().add(ctx.to.toJOML().sub(ctx.from.toJOML()).mul(chopParam.y))

        } else if (intersectType == Intersectionf.ONE_INTERSECTION) {
            // This intersection type will result in both calculations above returning the same point,
            // and thus we need to determine which point is inside the AABB to recover.
            if (expandedAABB.containsPoint(ctx.from.toJOML())) {
                choppedFrom = ctx.from.toJOML()
                choppedTo = ctx.from.toJOML().add(ctx.to.toJOML().sub(ctx.from.toJOML()).mul(chopParam.y))
            } else {
                choppedFrom = ctx.from.toJOML().add(ctx.to.toJOML().sub(ctx.from.toJOML()).mul(chopParam.x))
                choppedTo = ctx.to.toJOML()
            }

        } else {
            choppedFrom = ctx.from.toJOML()
            choppedTo = ctx.to.toJOML()
        }

        val worldToShip = (ship as? ClientShip)?.renderTransform?.worldToShip ?: ship.worldToShip
        val shipToWorld = (ship as? ClientShip)?.renderTransform?.shipToWorld ?: ship.shipToWorld
        ctx.rayCastExtraParameter._posStart = worldToShip.transformPosition(ctx.from.toJOML()).toMinecraft()
        ctx.rayCastExtraParameter._posEnd = worldToShip.transformPosition(ctx.to.toJOML()).toMinecraft()

        val shipHit = vanillaClip.call(ctx)
        ctx.rayCastExtraParameter._posStart = null
        ctx.rayCastExtraParameter._posEnd = null

        val shipHitPos = shipToWorld.transformPosition(shipHit.location.toJOML()).toMinecraft()
        val shipHitDist = shipHitPos.distanceToSqr(ctx.from)

        if (shipHitDist < closestHitDist && shipHit.type != HitResult.Type.MISS) {
            closestHit = shipHit
            closestHitPos = shipHitPos
            closestHitDist = shipHitDist
        }
    }

    if (ctx.rayCastExtraParameter.shouldTransformHitPos) {
        closestHit.location = closestHitPos
    }

    return closestHit
}

fun Level.raytraceEntities(
    shooter: Entity,
    origStartVecM: Vec3,
    origEndVecM: Vec3,
    origBoundingBoxM: AABB,
    filter: Predicate<Entity>,
    maxDistance2: Double
): EntityHitResult? {
    var distance2 = maxDistance2
    var resultEntity: Entity? = null
    var location: Vec3? = null

    fun checkEntities(entities: List<Entity>, startVec: Vec3, endVec: Vec3, scale: Double) =
        entities.forEach { entity ->
            val aabb = entity.boundingBox.inflate(entity.pickRadius.toDouble()).scale(scale)
            val clipO = aabb.clip(startVec, endVec)

            if (aabb.contains(startVec)) {
                if (distance2 < 0.0) return@forEach
                resultEntity = entity
                location = clipO.orElse(startVec)
                distance2 = 0.0
                return@forEach
            }

            if (!clipO.isPresent) return@forEach

            val clip = clipO.get()
            val d = startVec.distanceToSqr(clip) / (scale * scale)

            if (d >= distance2 && distance2 != 0.0) return@forEach

            if (entity.rootVehicle === shooter.rootVehicle) {
                if (distance2 != 0.0) return@forEach
                resultEntity = entity
                location = clip
                return@forEach
            }

            resultEntity = entity
            location = clip
            distance2 = d
        }

    val entities = getEntities(shooter, origBoundingBoxM, filter) // Returns world and ship-space entities (mixins)

    checkEntities(entities, origStartVecM, origEndVecM, 1.0)

    val origStartVec = origStartVecM.toJOML()
    val origEndVec = origEndVecM.toJOML()

    val start = Vector3d()
    val end = Vector3d()

    getShipsIntersecting(origBoundingBoxM.toJOML()).forEach {
        it.worldToShip.transformPosition(origStartVec, start)
        it.worldToShip.transformPosition(origEndVec, end)

        val scale = 1.0 / it.transform.shipToWorldScaling.x()

        checkEntities(entities, start.toMinecraft(), end.toMinecraft(), scale)
    }

    return if (resultEntity == null) {
        null
    } else EntityHitResult(resultEntity, location)
}

val VanillaClipContext = RayCastExtraParameter(useVanillaClip = true)

fun BlockGetter.vanillaClip(ctx: ClipContext): BlockHitResult {
    if (ctx !is ClipContextDuck)
        return this.clip(ctx)
    ctx.rayCastExtraParameter = VanillaClipContext
    val ret = this.clip(ctx)
    ctx.reset()
    return ret
}
