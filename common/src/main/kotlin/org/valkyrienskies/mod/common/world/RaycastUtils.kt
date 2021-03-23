package org.valkyrienskies.mod.common.world

import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.fluid.FluidState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toVec3d
import java.util.function.BiFunction
import java.util.function.Function

@JvmOverloads
fun ClientWorld.raycastIncludeShips(ctx: RaycastContext, shouldTransformHitPos: Boolean = true): BlockHitResult {
    val vanillaHit = raycast(ctx)

    var closestHit = vanillaHit
    var closestHitPos = vanillaHit.pos
    var closestHitDist = closestHitPos.squaredDistanceTo(ctx.start)

    // TODO make this more efficient
    // Iterate every ship, find do the raycast in ship space,
    // choose the raycast with the lowest distance to the start position.
    for (ship in shipObjectWorld.shipObjects.values) {
        val worldToShip = ship.renderTransform.worldToShipMatrix
        val shipToWorld = ship.renderTransform.shipToWorldMatrix
        val shipStart = worldToShip.transformPosition(ctx.start.toJOML()).toVec3d()
        val shipEnd = worldToShip.transformPosition(ctx.end.toJOML()).toVec3d()

        val shipHit = raycast(ctx, shipStart, shipEnd)
        val shipHitPos = shipToWorld.transformPosition(shipHit.pos.toJOML()).toVec3d()
        val shipHitDist = shipHitPos.squaredDistanceTo(ctx.start)

        if (shipHitDist < closestHitDist && shipHit.type != HitResult.Type.MISS) {
            closestHit = shipHit
            closestHitPos = shipHitPos
            closestHitDist = shipHitDist
        }
    }

    if (!shouldTransformHitPos) {
        closestHitPos = closestHit.pos
    }

    return if (closestHit.type == HitResult.Type.MISS) {
        BlockHitResult.createMissed(closestHitPos, closestHit.side, closestHit.blockPos)
    } else {
        BlockHitResult(closestHitPos, closestHit.side, closestHit.blockPos, closestHit.isInsideBlock)
    }
}

// copy paste of vanilla raycast with the option to specify a custom start/end
private fun World.raycast(context: RaycastContext, realStart: Vec3d, realEnd: Vec3d): BlockHitResult {
    return raycast(
        realStart, realEnd, context,
        { raycastContext: RaycastContext, blockPos: BlockPos? ->
            val blockState: BlockState = getBlockState(blockPos)
            val fluidState: FluidState = getFluidState(blockPos)
            val vec3d = realStart
            val vec3d2 = realEnd
            val voxelShape = raycastContext.getBlockShape(blockState, this, blockPos)
            val blockHitResult: BlockHitResult? = raycastBlock(vec3d, vec3d2, blockPos, voxelShape, blockState)
            val voxelShape2 = raycastContext.getFluidShape(fluidState, this, blockPos)
            val blockHitResult2 = voxelShape2.raycast(vec3d, vec3d2, blockPos)
            val d = if (blockHitResult == null) Double.MAX_VALUE else realStart.squaredDistanceTo(blockHitResult.pos)
            val e = if (blockHitResult2 == null) Double.MAX_VALUE else realEnd.squaredDistanceTo(blockHitResult2.pos)
            if (d <= e) blockHitResult else blockHitResult2
        }
    ) { raycastContext: RaycastContext ->
        val vec3d = realStart.subtract(realEnd)
        BlockHitResult.createMissed(realEnd, Direction.getFacing(vec3d.x, vec3d.y, vec3d.z), BlockPos(realEnd))
    } as BlockHitResult
}

private fun <T> raycast(
    realStart: Vec3d,
    realEnd: Vec3d,
    raycastContext: RaycastContext,
    context: BiFunction<RaycastContext, BlockPos?, T>,
    blockRaycaster: Function<RaycastContext, T>
): T {
    val vec3d = realStart
    val vec3d2 = realEnd
    return if (vec3d == vec3d2) {
        blockRaycaster.apply(raycastContext)
    } else {
        val d = MathHelper.lerp(-1.0E-7, vec3d2.x, vec3d.x)
        val e = MathHelper.lerp(-1.0E-7, vec3d2.y, vec3d.y)
        val f = MathHelper.lerp(-1.0E-7, vec3d2.z, vec3d.z)
        val g = MathHelper.lerp(-1.0E-7, vec3d.x, vec3d2.x)
        val h = MathHelper.lerp(-1.0E-7, vec3d.y, vec3d2.y)
        val i = MathHelper.lerp(-1.0E-7, vec3d.z, vec3d2.z)
        var j = MathHelper.floor(g)
        var k = MathHelper.floor(h)
        var l = MathHelper.floor(i)
        val mutable = BlockPos.Mutable(j, k, l)
        val `object`: T? = context.apply(raycastContext, mutable)
        if (`object` != null) {
            `object`
        } else {
            val m = d - g
            val n = e - h
            val o = f - i
            val p = MathHelper.sign(m)
            val q = MathHelper.sign(n)
            val r = MathHelper.sign(o)
            val s = if (p == 0) Double.MAX_VALUE else p.toDouble() / m
            val t = if (q == 0) Double.MAX_VALUE else q.toDouble() / n
            val u = if (r == 0) Double.MAX_VALUE else r.toDouble() / o
            var v = s * if (p > 0) 1.0 - MathHelper.fractionalPart(g) else MathHelper.fractionalPart(g)
            var w = t * if (q > 0) 1.0 - MathHelper.fractionalPart(h) else MathHelper.fractionalPart(h)
            var x = u * if (r > 0) 1.0 - MathHelper.fractionalPart(i) else MathHelper.fractionalPart(i)
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
