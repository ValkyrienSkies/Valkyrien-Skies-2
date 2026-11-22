package org.valkyrienskies.mod.common.util

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.valkyrienskies.mod.common.allShips
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames
import org.valkyrienskies.mod.common.retainedUsePiece
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import java.util.concurrent.Executor
import java.util.function.Supplier
import org.apache.logging.log4j.LogManager
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.vsCore

// Vec3/Vec3i carry no Level, and shipyard chunk claims can overlap between dimensions, so the level is inferred:
// server thread -> the context level (set around level ticks, player ticks, packets and commands), else the single server level
// owning that chunk (vanilla if several do);
// any other thread on a physical client -> the client level; otherwise vanilla is left alone.
// Points on the same ship (and split piece) keep their shipyard distance (also keeps scaled ships in ship units).
object ShipAwareDistance {

    private val LOGGER = LogManager.getLogger("[Valkyrien Skies] Ship distance")
    private val loggedCallSites: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Only touched on the server thread.
    private var contextLevel: ServerLevel? = null

    /** Runs [body] with [level] as the context for level-less distance checks. */
    @JvmStatic
    fun <T> withLevel(level: ServerLevel, body: Supplier<T>): T {
        if (!level.server.isSameThread) return body.get()
        val previous = contextLevel
        contextLevel = level
        try {
            return body.get()
        } finally {
            contextLevel = previous
        }
    }

    /** [work] runs with [level] as the context, whenever it is eventually run (for deferred packet work). */
    @JvmStatic
    fun bindLevel(level: ServerLevel, work: Runnable): Runnable = Runnable { withLevel(level, Supplier { work.run() }) }

    @JvmStatic
    fun bindLevel(level: ServerLevel, executor: Executor): Executor = Executor { executor.execute(bindLevel(level, it)) }

    /** Returns NaN when vanilla's own result is already correct. */
    @JvmStatic
    fun distanceSqr(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double {
        val w = toWorld(x1, y1, z1, x2, y2, z2) ?: return Double.NaN
        if (w === GONE) return Double.POSITIVE_INFINITY
        val dx = w[3] - w[0]
        val dy = w[4] - w[1]
        val dz = w[5] - w[2]
        return dx * dx + dy * dy + dz * dz
    }

    /** Returns Int.MIN_VALUE when vanilla's own result is already correct. */
    @JvmStatic
    fun distManhattan(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): Int {
        val w = toWorld(x1.toDouble(), y1.toDouble(), z1.toDouble(), x2.toDouble(), y2.toDouble(), z2.toDouble())
            ?: return Int.MIN_VALUE
        if (w === GONE) return Int.MAX_VALUE
        return abs(Mth.floor(w[3]) - Mth.floor(w[0])) + abs(Mth.floor(w[4]) - Mth.floor(w[1])) +
            abs(Mth.floor(w[5]) - Mth.floor(w[2]))
    }

    // A point no split piece retains no longer exists physically; it reads as infinitely far.
    private val GONE = DoubleArray(6)

    private class Located(val ship: Ship, val level: Level)

    /** Both points in world space as [x1, y1, z1, x2, y2, z2], null when no transform is needed, or [GONE]. */
    private fun toWorld(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): DoubleArray? {
        val cx1 = Mth.floor(x1) shr 4
        val cz1 = Mth.floor(z1) shr 4
        val cx2 = Mth.floor(x2) shr 4
        val cz2 = Mth.floor(z2) shr 4
        val inShipyard1 = vsCore.isChunkInShipyard(cx1, cz1)
        val inShipyard2 = vsCore.isChunkInShipyard(cx2, cz2)
        if (!inShipyard1 && !inShipyard2) return null

        val loc1 = if (inShipyard1) locate(cx1, cz1) else null
        val loc2 = if (inShipyard2) locate(cx2, cz2) else null
        if (loc1 == null && loc2 == null) return null

        val pieces1 = loc1?.let { it.level.allShips.getSplitPieces(it.ship.id) }.orEmpty()
        val pieces2 = loc2?.let { it.level.allShips.getSplitPieces(it.ship.id) }.orEmpty()
        val sameShip = loc1 != null && loc2 != null && loc1.ship.id == loc2.ship.id
        if (sameShip && pieces1.isEmpty()) return null

        // Split pieces are chosen by the other point's rough (whole-ship) world position.
        val piece1 = if (pieces1.isEmpty()) null else retainedUsePiece(pieces1, Vec3(x1, y1, z1),
            loc2?.let { transform(wholeShip(it), x2, y2, z2) } ?: Vec3(x2, y2, z2), BlockPos.containing(x1, y1, z1)) ?: return GONE
        val piece2 = if (pieces2.isEmpty()) null else retainedUsePiece(pieces2, Vec3(x2, y2, z2),
            loc1?.let { transform(wholeShip(it), x1, y1, z1) } ?: Vec3(x1, y1, z1), BlockPos.containing(x2, y2, z2)) ?: return GONE
        if (sameShip && piece1 === piece2) return null

        val w1 = loc1?.let { transform(matrixFor(it, piece1), x1, y1, z1) } ?: Vec3(x1, y1, z1)
        val w2 = loc2?.let { transform(matrixFor(it, piece2), x2, y2, z2) } ?: Vec3(x2, y2, z2)
        return doubleArrayOf(w1.x, w1.y, w1.z, w2.x, w2.y, w2.z)
    }

    /**
     * For a split ship the point belongs to the piece that still holds it (nearest to the other point); a point no
     * piece holds is gone. Clients use interpolated render transforms.
     */
    private fun matrixFor(loc: Located, piece: ShipPhysicalPiece?): Matrix4dc = when {
        piece == null -> wholeShip(loc)
        loc.level.isClientSide -> Client.renderedShipToWorld(piece)
        else -> piece.shipToWorld
    }

    private fun wholeShip(loc: Located): Matrix4dc =
        (loc.ship as? ClientShip)?.renderTransform?.shipToWorld ?: loc.ship.shipToWorld

    private fun transform(m: Matrix4dc, x: Double, y: Double, z: Double) = Vec3(
        m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
        m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
        m.m02() * x + m.m12() * y + m.m22() * z + m.m32()
    )

    private fun locate(chunkX: Int, chunkZ: Int): Located? {
        val server = ValkyrienSkiesMod.currentServer
        if (server != null && server.isSameThread) {
            contextLevel?.let { level -> return level.getShipManagingPos(chunkX, chunkZ)?.let { Located(it, level) } }
            var found: Located? = null
            for (level in server.allLevels) {
                val ship = level.getShipManagingPos(chunkX, chunkZ) ?: continue
                if (found != null) {
                    logUnresolved("chunk claimed in several dimensions and no context level")
                    return null
                }
                found = Located(ship, level)
            }
            return found
        }
        if (!vsCore.hooks.isPhysicalClient) {
            logUnresolved("off the server thread on a dedicated server")
            return null
        }
        val level = Client.level() ?: return null
        return level.getShipManagingPos(chunkX, chunkZ)?.let { Located(it, level) }
    }

    private fun logUnresolved(reason: String) {
        if (!VSGameConfig.SERVER.logUnresolvedShipDistanceChecks || loggedCallSites.size >= 256) return
        val frames = StackWalker.getInstance().walk { stream ->
            stream.map { "${it.className}.${it.methodName}:${it.lineNumber}" }
                .filter { !it.startsWith(ShipAwareDistance::class.java.name) && !it.startsWith("net.minecraft.world.phys.Vec3.") && !it.startsWith("net.minecraft.core.Vec3i.") }
                .limit(6)
                .toList()
        }
        if (frames.isEmpty() || !loggedCallSites.add(frames[0])) return
        LOGGER.warn("Unresolved ship distance check ({}) at:\n    {}", reason, frames.joinToString("\n    "))
    }

    // Separate class so dedicated servers never resolve Minecraft.
    private object Client {
        fun level(): Level? = Minecraft.getInstance().level
        fun renderedShipToWorld(piece: ShipPhysicalPiece): Matrix4dc = ShipPieceRenderFrames.rendered(piece).shipToWorld
    }
}
