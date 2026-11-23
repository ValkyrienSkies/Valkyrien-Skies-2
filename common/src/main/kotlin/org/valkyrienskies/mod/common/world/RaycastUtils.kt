package org.valkyrienskies.mod.common.world

import net.minecraft.client.multiplayer.ClientLevel
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.toShipRenderCoordinates
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4dc
import org.joml.Intersectiond
import org.joml.Vector2d
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.valkyrienskies.mod.common.allShips
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.getRenderShipsIntersecting
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.ShipyardEntityQueryContext
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.util.scale
import java.util.Vector
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate

private val logger = LogManager.getLogger("RaycastUtilsKt")

private class MappedShipHit(world: Vec3, val local: BlockHitResult) :
    BlockHitResult(world, local.direction, local.blockPos, local.isInside)

@JvmOverloads
fun ClientLevel?.blockHitForUse(hit: BlockHitResult, tickGeometry: Boolean = RayQuery.frameTime() == null): BlockHitResult {
    if (hit is MappedShipHit) return hit.local
    if (hit.type == HitResult.Type.MISS || hit.blockPos.center.closerThan(hit.location, 1.0)) return hit
    val local = if (tickGeometry) getLoadedShipManagingPos(hit.blockPos)?.worldToShip
        ?.transformPosition(hit.location.toJOML())?.toMinecraft() ?: hit.location
    else toShipRenderCoordinates(hit.blockPos.center, hit.location)
    return if (local == hit.location) hit else BlockHitResult(local, hit.direction, hit.blockPos, hit.isInside)
}

@JvmOverloads
fun Level.clipIncludeShips(
    ctx: ClipContext, shouldTransformHitPos: Boolean = true, skipShip: ShipId? = null, skipWorld: Boolean = false
): BlockHitResult {
    val vanillaHit = if(skipWorld) {
        val line = ctx.to.subtract(ctx.from)
        BlockHitResult.miss(ctx.to, Direction.getNearest(line.x, line.y, line.z), BlockPos.containing(ctx.to))
    } else vanillaClip(ctx)

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

    val clipAABB = AABBd(ctx.from.toJOML(), ctx.to.toJOML()).correctBounds()

    // Iterate every ship, find do the raycast in ship space,
    // choose the raycast with the lowest distance to the start position.
    val render = RayQuery.frameTime() != null && isClientSide()
    val ships = if (render && this is ClientLevel) getRenderShipsIntersecting(clipAABB) else getShipsIntersecting(clipAABB)
    for (ship in ships) {
        val split = allShips.getSplitPieces(ship.id)
        if (split.isNotEmpty()) {
            if (ship.id == skipShip) continue
            for (physical in split) {
                if (ship is ClientShip && physical.dimensionId != dimensionId) continue
                val piece = if (render && ship is ClientShip) ShipPieceRenderFrames.rendered(physical) else physical
                if (!piece.worldBounds.intersectsAABB(clipAABB)) continue
                val segment = clipToPiece(ctx.from, ctx.to, piece) ?: continue
                val start = piece.worldToShip.transformPosition(segment.first.toJOML()).toMinecraft()
                val end = piece.worldToShip.transformPosition(segment.second.toJOML()).toMinecraft()
                val hit = clip(ctx, start, end)
                if (hit.type == HitResult.Type.MISS) continue
                val pos = piece.shipToWorld.transformPosition(hit.location.toJOML()).toMinecraft()
                val distance = ctx.from.distanceToSqr(pos)
                if (distance < closestHitDist) {
                    closestHit = hit
                    closestHitPos = pos
                    closestHitDist = distance
                }
            }
            continue
        }
        val chopParam = Vector2d()
        // Pad AABB size to increase raycast tolerance
        val expandedAABB = AABBd(if (render && ship is ClientShip) ship.renderAABB else ship.worldAABB).expand(1.0)
        val intersectType = expandedAABB.intersectsLineSegment(
            ctx.from.x, ctx.from.y, ctx.from.z, ctx.to.x, ctx.to.y, ctx.to.z, chopParam)
        if (intersectType == Intersectiond.OUTSIDE) {
            continue
        }
        // Skip skipShip
        if (ship.id == skipShip) {
            continue
        }

        var choppedFrom: Vector3d
        var choppedTo : Vector3d
        if (intersectType == Intersectiond.TWO_INTERSECTION) {
            choppedFrom = ctx.from.toJOML().add(ctx.to.toJOML().sub(ctx.from.toJOML()).mul(chopParam.x))
            choppedTo = ctx.from.toJOML().add(ctx.to.toJOML().sub(ctx.from.toJOML()).mul(chopParam.y))

        } else if (intersectType == Intersectiond.ONE_INTERSECTION) {
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

        val worldToShip = if (render && ship is ClientShip) ship.renderTransform.worldToShip else ship.worldToShip
        val shipToWorld = if (render && ship is ClientShip) ship.renderTransform.shipToWorld else ship.shipToWorld
        val shipStart = worldToShip.transformPosition(choppedFrom).toMinecraft()
        val shipEnd = worldToShip.transformPosition(choppedTo).toMinecraft()

        val shipHit = clip(ctx, shipStart, shipEnd)
        val shipHitPos = shipToWorld.transformPosition(shipHit.location.toJOML()).toMinecraft()
        val shipHitDist = shipHitPos.distanceToSqr(ctx.from)

        if (shipHitDist < closestHitDist && shipHit.type != HitResult.Type.MISS) {
            closestHit = shipHit
            closestHitPos = shipHitPos
            closestHitDist = shipHitDist
        }
    }

    return if (shouldTransformHitPos && closestHit !== vanillaHit) MappedShipHit(closestHitPos, closestHit) else closestHit
}

internal data class RetainedRay(val from: Vec3, val to: Vec3, val enters: Boolean, val pointOnly: Boolean)

internal fun retainedRay(from: Vec3, to: Vec3, piece: ShipPhysicalPiece): RetainedRay? {
    val delta = to.subtract(from)
    var first = 0.0
    var last = 1.0
    var enters = false
    for (plane in piece.clippingPlanes) {
        val start = plane.x() * from.x + plane.y() * from.y + plane.z() * from.z + plane.w()
        val slope = plane.x() * delta.x + plane.y() * delta.y + plane.z() * delta.z
        if (slope == 0.0) {
            if (start < 0.0) return null
        } else {
            val t = -start / slope
            if (slope > 0.0) {
                if (t >= first) { first = t; enters = true } // includes inward crossing at t = +/-0
            } else last = minOf(last, t)
            if (first > last) return null
        }
    }
    return RetainedRay(from.add(delta.scale(first)), from.add(delta.scale(last)), enters, first == last)
}

internal fun clipToPiece(from: Vec3, to: Vec3, piece: ShipPhysicalPiece): Pair<Vec3, Vec3>? =
    retainedRay(from, to, piece)?.takeUnless { it.pointOnly }?.let { it.from to it.to }

// copy paste of vanilla raycast with the option to specify a custom start/end
private fun Level.clip(context: ClipContext, realStart: Vec3, realEnd: Vec3): BlockHitResult {
    return clip(
        realStart, realEnd, context,
        cell@ { raycastContext: ClipContext, blockPos: BlockPos? ->
            val pos = blockPos!!
            val chunk = chunkSource.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false)
                ?: return@cell null
            val blockState: BlockState = chunk.getBlockState(pos)
            val fluidState: FluidState = chunk.getFluidState(pos)
            val vec3d = realStart
            val vec3d2 = realEnd
            val voxelShape = raycastContext.getBlockShape(blockState, this, blockPos)
            val blockHitResult: BlockHitResult? =
                clipWithInteractionOverride(vec3d, vec3d2, blockPos, voxelShape, blockState)
            val voxelShape2 = raycastContext.getFluidShape(fluidState, this, blockPos)
            val blockHitResult2 = voxelShape2.clip(vec3d, vec3d2, blockPos)
            val d = if (blockHitResult == null) Double.MAX_VALUE else realStart.distanceToSqr(blockHitResult.location)
            val e = if (blockHitResult2 == null) Double.MAX_VALUE else realStart.distanceToSqr(blockHitResult2.location)
            if (d <= e) blockHitResult else blockHitResult2
        }
    ) { raycastContext: ClipContext ->
        val vec3d = realStart.subtract(realEnd)
        BlockHitResult.miss(realEnd, Direction.getNearest(vec3d.x, vec3d.y, vec3d.z), BlockPos.containing(realEnd))
    } as BlockHitResult
}

private fun <T> clip(
    realStart: Vec3,
    realEnd: Vec3,
    raycastContext: ClipContext,
    context: BiFunction<ClipContext, BlockPos?, T>,
    blockRaycaster: Function<ClipContext, T>
): T {
    val vec3d = realStart
    val vec3d2 = realEnd
    return if (vec3d == vec3d2) {
        blockRaycaster.apply(raycastContext)
    } else {
        val d = Mth.lerp(-1.0E-7, vec3d2.x, vec3d.x)
        val e = Mth.lerp(-1.0E-7, vec3d2.y, vec3d.y)
        val f = Mth.lerp(-1.0E-7, vec3d2.z, vec3d.z)
        val g = Mth.lerp(-1.0E-7, vec3d.x, vec3d2.x)
        val h = Mth.lerp(-1.0E-7, vec3d.y, vec3d2.y)
        val i = Mth.lerp(-1.0E-7, vec3d.z, vec3d2.z)
        var j = Mth.floor(g)
        var k = Mth.floor(h)
        var l = Mth.floor(i)
        val mutable = BlockPos.MutableBlockPos(j, k, l)
        val `object`: T? = context.apply(raycastContext, mutable)
        if (`object` != null) {
            `object`
        } else {
            val m = d - g
            val n = e - h
            val o = f - i
            val p = Mth.sign(m)
            val q = Mth.sign(n)
            val r = Mth.sign(o)
            val s = if (p == 0) Double.MAX_VALUE else p.toDouble() / m
            val t = if (q == 0) Double.MAX_VALUE else q.toDouble() / n
            val u = if (r == 0) Double.MAX_VALUE else r.toDouble() / o
            var v = s * if (p > 0) 1.0 - Mth.frac(g) else Mth.frac(g)
            var w = t * if (q > 0) 1.0 - Mth.frac(h) else Mth.frac(h)
            var x = u * if (r > 0) 1.0 - Mth.frac(i) else Mth.frac(i)
            var object2: T?
            do {
                if (v > 1.0 && w > 1.0 && x > 1.0) {
                    return blockRaycaster.apply(raycastContext)
                }
                if (v < w) {
                    if (v < x) {
                        j += p
                        v += s
                    } else {
                        l += r
                        x += u
                    }
                } else if (w < x) {
                    k += q
                    w += t
                } else {
                    l += r
                    x += u
                }
                object2 = context.apply(raycastContext, mutable.set(j, k, l))
            } while (object2 == null)
            object2
        }
    }
}

class MappedEntityHit(entity: Entity, world: Vec3, val relative: Vec3, val physical: ShipPhysicalPiece?) : EntityHitResult(entity, world)

fun Level.raytraceEntities(
    shooter: Entity,
    origStartVecM: Vec3,
    origEndVecM: Vec3,
    origBoundingBoxM: AABB,
    filter: Predicate<Entity>,
    maxDistance2: Double
): EntityHitResult? {
    return traceShipActors(shooter, origStartVecM, origEndVecM, origBoundingBoxM, filter, maxDistance2, null)
}

/** The projectile overload always uses game geometry and its explicit world-sized padding. */
fun Level.raytraceEntitiesInflated(shooter: Entity, from: Vec3, to: Vec3, bounds: AABB,
                                  filter: Predicate<Entity>, inflate: Float): EntityHitResult? =
    traceShipActors(shooter, from, to, bounds, filter, Double.MAX_VALUE, inflate.toDouble())

private fun Level.traceShipActors(shooter: Entity, from: Vec3, to: Vec3, worldBounds: AABB,
                                  filter: Predicate<Entity>, limit: Double, projectilePadding: Double?): EntityHitResult? {
    val render = projectilePadding == null && RayQuery.isFrameEntityPick() && this is ClientLevel
    val bounds = worldBounds.toJOML()
    val ships = if (render && this is ClientLevel) getRenderShipsIntersecting(bounds) else getShipsIntersecting(bounds)
    var best: EntityHitResult? = null
    var distance = limit
    for (ship in ships) forEachRayImage(ship, from, to, render) { image ->
        val localBounds = AABBd(bounds).transform(image.inverse).toMinecraft()
        ShipyardEntityQueryContext.pushShipSpaceQuery(localBounds, ship.id)
        val candidates = try { getEntities(shooter, localBounds, filter) } finally { ShipyardEntityQueryContext.popShipSpaceQuery() }
        for (entity in candidates) {
            if (projectilePadding == null && shooter.rootVehicle === entity.rootVehicle) continue
            val local = image.clip(entity.boundingBox, projectilePadding ?: entity.pickRadius.toDouble(), projectilePadding == null) ?: continue
            val world = image.world(local)
            val d = from.distanceToSqr(world)
            if (d < distance) {
                distance = d
                best = MappedEntityHit(entity, world, local.subtract(entity.position()), image.physical)
            }
        }
    }
    return best
}

fun BlockGetter.vanillaClip(context: ClipContext): BlockHitResult =
    BlockGetter.traverseBlocks(context.from, context.to, context,
        { clipContext: ClipContext, blockPos: BlockPos ->
            val blockState = getBlockState(blockPos)
            val fluidState = getFluidState(blockPos)
            val vec3 = clipContext.from
            val vec32 = clipContext.to
            val voxelShape = clipContext.getBlockShape(blockState, this, blockPos)
            val blockHitResult = clipWithInteractionOverride(vec3, vec32, blockPos, voxelShape, blockState)
            val voxelShape2 = clipContext.getFluidShape(fluidState, this, blockPos)
            val blockHitResult2 = voxelShape2.clip(vec3, vec32, blockPos)

            val d = if (blockHitResult == null)
                Double.MAX_VALUE
            else
                clipContext.from.distanceToSqr(blockHitResult.location)

            val e = if (blockHitResult2 == null)
                Double.MAX_VALUE
            else
                clipContext.from.distanceToSqr(blockHitResult2.location)

            if (d <= e)
                blockHitResult
            else
                blockHitResult2
        }, { ctx ->
            val vec3 = ctx.from.subtract(ctx.to)
            BlockHitResult.miss(
                ctx.to, Direction.getNearest(vec3.x, vec3.y, vec3.z),
                BlockPos.containing(ctx.to)
            )
        })
