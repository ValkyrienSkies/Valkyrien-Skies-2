package org.valkyrienskies.mod.common.world

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toVec3d
import java.util.function.BiFunction
import java.util.function.Function

@JvmOverloads
fun ClientLevel.clipIncludeShips(ctx: ClipContext, shouldTransformHitPos: Boolean = true): BlockHitResult {
    val vanillaHit = clip(ctx)

    var closestHit = vanillaHit
    var closestHitPos = vanillaHit.location
    var closestHitDist = closestHitPos.distanceToSqr(ctx.from)

    // TODO make this more efficient
    // Iterate every ship, find do the raycast in ship space,
    // choose the raycast with the lowest distance to the start position.
    for (ship in shipObjectWorld.shipObjects.values) {
        val worldToShip = ship.renderTransform.worldToShipMatrix
        val shipToWorld = ship.renderTransform.shipToWorldMatrix
        val shipStart = worldToShip.transformPosition(ctx.from.toJOML()).toVec3d()
        val shipEnd = worldToShip.transformPosition(ctx.to.toJOML()).toVec3d()

        val shipHit = clip(ctx, shipStart, shipEnd)
        val shipHitPos = shipToWorld.transformPosition(shipHit.location.toJOML()).toVec3d()
        val shipHitDist = shipHitPos.distanceToSqr(ctx.from)

        if (shipHitDist < closestHitDist && shipHit.type != HitResult.Type.MISS) {
            closestHit = shipHit
            closestHitPos = shipHitPos
            closestHitDist = shipHitDist
        }
    }

    if (!shouldTransformHitPos) {
        closestHitPos = closestHit.location
    }

    return if (closestHit.type == HitResult.Type.MISS) {
        BlockHitResult.miss(closestHitPos, closestHit.direction, closestHit.blockPos)
    } else {
        BlockHitResult(closestHitPos, closestHit.direction, closestHit.blockPos, closestHit.isInside)
    }
}

// copy paste of vanilla raycast with the option to specify a custom start/end
private fun Level.clip(context: ClipContext, realStart: Vec3, realEnd: Vec3): BlockHitResult {
    return clip(
        realStart, realEnd, context,
        { raycastContext: ClipContext, blockPos: BlockPos? ->
            val blockState: BlockState = getBlockState(blockPos)
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
