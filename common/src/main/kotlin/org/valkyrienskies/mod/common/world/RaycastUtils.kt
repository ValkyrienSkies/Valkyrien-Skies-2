package org.valkyrienskies.mod.common.world

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
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.util.scale
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate

private val logger = LogManager.getLogger("RaycastUtilsKt")

@JvmOverloads
fun Level.clipIncludeShips(
    ctx: ClipContext, shouldTransformHitPos: Boolean = true, skipShip: ShipId? = null
): BlockHitResult {
    val vanillaHit = vanillaClip(ctx)

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

    // Iterate every ship, find do the raycast in ship space,
    // choose the raycast with the lowest distance to the start position.
    for (ship in shipObjectWorld.loadedShips.getIntersecting(clipAABB)) {
        // Skip skipShip
        if (ship.id == skipShip) {
            continue
        }
        val worldToShip = (ship as? ShipObjectClient)?.renderTransform?.worldToShipMatrix ?: ship.worldToShip
        val shipToWorld = (ship as? ShipObjectClient)?.renderTransform?.shipToWorldMatrix ?: ship.shipToWorld
        val shipStart = worldToShip.transformPosition(ctx.from.toJOML()).toMinecraft()
        val shipEnd = worldToShip.transformPosition(ctx.to.toJOML()).toMinecraft()

        val shipHit = clip(ctx, shipStart, shipEnd)
        val shipHitPos = shipToWorld.transformPosition(shipHit.location.toJOML()).toMinecraft()
        val shipHitDist = shipHitPos.distanceToSqr(ctx.from)

        if (shipHitDist < closestHitDist && shipHit.type != HitResult.Type.MISS) {
            closestHit = shipHit
            closestHitPos = shipHitPos
            closestHitDist = shipHitDist
        }
    }

    if (shouldTransformHitPos) {
        closestHit.location = closestHitPos
    }

    return closestHit
}

// copy paste of vanilla raycast with the option to specify a custom start/end
private fun Level.clip(context: ClipContext, realStart: Vec3, realEnd: Vec3): BlockHitResult {
    return clip(
        realStart, realEnd, context,
        { raycastContext: ClipContext, blockPos: BlockPos? ->
            val blockState: BlockState = getBlockState(blockPos!!)
            val fluidState: FluidState = getFluidState(blockPos)
            val vec3d = realStart
            val vec3d2 = realEnd
            val voxelShape = raycastContext.getBlockShape(blockState, this, blockPos)
            val blockHitResult: BlockHitResult? =
                clipWithInteractionOverride(vec3d, vec3d2, blockPos, voxelShape, blockState)
            val voxelShape2 = raycastContext.getFluidShape(fluidState, this, blockPos)
            val blockHitResult2 = voxelShape2.clip(vec3d, vec3d2, blockPos)
            val d = if (blockHitResult == null) Double.MAX_VALUE else realStart.distanceToSqr(blockHitResult.location)
            val e = if (blockHitResult2 == null) Double.MAX_VALUE else realEnd.distanceToSqr(blockHitResult2.location)
            if (d <= e) blockHitResult else blockHitResult2
        }
    ) { raycastContext: ClipContext ->
        val vec3d = realStart.subtract(realEnd)
        BlockHitResult.miss(realEnd, Direction.getNearest(vec3d.x, vec3d.y, vec3d.z), BlockPos(realEnd))
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

    shipObjectWorld.loadedShips.getIntersecting(origBoundingBoxM.toJOML()).forEach {
        it.worldToShip.transformPosition(origStartVec, start)
        it.worldToShip.transformPosition(origEndVec, end)

        val scale = 1.0 / it.transform.shipToWorldScaling.x()

        checkEntities(entities, start.toMinecraft(), end.toMinecraft(), scale)
    }

    return if (resultEntity == null) {
        null
    } else EntityHitResult(resultEntity, location)
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
                BlockPos(ctx.to)
            )
        })
