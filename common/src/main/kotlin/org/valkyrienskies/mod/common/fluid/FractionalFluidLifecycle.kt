package org.valkyrienskies.mod.common.fluid

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.LevelChunk
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources.Position
import java.math.BigDecimal

class FractionalFluidTransferScope internal constructor(private val finish: () -> Unit, private val markComplete: () -> Unit) : AutoCloseable {
    private var closed = false
    fun complete() { check(!closed); markComplete() }
    override fun close() {
        if (closed) return
        closed = true
        finish()
    }
}

object FractionalFluidLifecycle {
    fun beginMove(level: ServerLevel, blocks: Set<BlockPos>, source: ServerShip?, destination: ServerShip?,
        fromCorner: BlockPos, toCorner: BlockPos, copy: Boolean): FractionalFluidTransferScope? {
        val sources = FractionalFluidSources.forLevel(level)?.takeIf { it.size != 0 } ?: return null
        val dimension = level.dimensionId
        val dx = Math.subtractExact(toCorner.x, fromCorner.x)
        val dy = Math.subtractExact(toCorner.y, fromCorner.y)
        val dz = Math.subtractExact(toCorner.z, fromCorner.z)
        val moves = blocks.asSequence().filter { sources.amounts.containsKey(Position(dimension, it.x, it.y, it.z)) }.map { pos ->
            request(Position(dimension, pos.x, pos.y, pos.z),
                Position(dimension, Math.addExact(pos.x, dx), Math.addExact(pos.y, dy), Math.addExact(pos.z, dz)),
                source, destination, copy)
        }
        return sources.transfers.begin(moves, mapOf(dimension to level), parcels(level))
    }

    @JvmStatic
    fun beginDimensionMove(source: ServerLevel, destination: ServerLevel, chunks: IShipActiveChunksSet): FractionalFluidTransferScope? {
        val sources = FractionalFluidSources.forLevel(source)?.takeIf { it.size != 0 } ?: return null
        val fromDimension = source.dimensionId
        val toDimension = destination.dimensionId
        val moves = sources.amounts.keys.asSequence().filter {
            it.dimension == fromDimension && chunks.contains(it.x shr 4, it.z shr 4)
        }.map { pos ->
            val ship = source.getShipManagingPos(BlockPos(pos.x, pos.y, pos.z))
            request(pos, pos.copy(dimension = toDimension), ship, ship, false)
        }
        return sources.transfers.begin(moves, mapOf(fromDimension to source, toDimension to destination), parcels(source))
    }

    @JvmStatic
    fun releaseShip(level: ServerLevel, ship: ServerShip) {
        val sources = FractionalFluidSources.forLevel(level)?.takeIf { it.size != 0 } ?: return
        releaseShip(sources, mapOf(level.dimensionId to level), parcels(level), ship)
    }

    @JvmStatic
    fun releaseShip(sources: FractionalFluidSources, levels: Map<String, ServerLevel>,
        parcels: WorldFluidOutflowManager, ship: ServerShip) {
        if (sources.size == 0) return
        val dimension = ship.chunkClaimDimension
        val moves = sources.amounts.entries.asSequence().filter {
            it.key.dimension == dimension && it.value.units.signum() > 0 && ship.activeChunksSet.contains(it.key.x shr 4, it.key.z shr 4)
        }.map { request(it.key, null, ship, null, false) }
        sources.transfers.begin(moves, levels, parcels)?.close()
    }

    fun beginBlockMove(fromChunk: LevelChunk, from: BlockPos, toChunk: LevelChunk, to: BlockPos,
        destination: ServerShip?): FractionalFluidTransferScope? {
        val level = fromChunk.level as? ServerLevel ?: return null
        val sources = FractionalFluidSources.forLevel(level)?.takeIf { it.size != 0 } ?: return null
        val fromPos = Position(level.dimensionId, (fromChunk.pos.x shl 4) + (from.x and 15), from.y,
            (fromChunk.pos.z shl 4) + (from.z and 15))
        if (!sources.amounts.containsKey(fromPos)) return null
        val targetLevel = toChunk.level as ServerLevel
        check(FractionalFluidSources.forLevel(targetLevel) === sources)
        val toPos = Position(targetLevel.dimensionId, (toChunk.pos.x shl 4) + (to.x and 15), to.y,
            (toChunk.pos.z shl 4) + (to.z and 15))
        val move = request(fromPos, toPos, level.getShipManagingPos(BlockPos(fromPos.x, fromPos.y, fromPos.z)), destination, false)
        return sources.transfers.begin(sequenceOf(move), mapOf(fromPos.dimension to level, toPos.dimension to targetLevel), parcels(level))
    }

    private fun parcels(level: ServerLevel): WorldFluidOutflowManager =
        checkNotNull((level.server as IShipObjectWorldServerProvider).fluidOutflowManager)

    private fun request(from: Position, to: Position?, source: ServerShip?, destination: ServerShip?, copy: Boolean): FractionalFluidTransfers.Move {
        val position = Vector3d(from.x + 0.5, from.y + 0.5, from.z + 0.5)
        source?.shipToWorld?.transformPosition(position)
        val velocity = if (source == null) Vector3d() else Vector3d(source.angularVelocity)
            .cross(Vector3d(position).sub(source.transform.positionInWorld)).add(source.velocity)
        val targetPosition = to?.let { Vector3d(it.x + 0.5, it.y + 0.5, it.z + 0.5) } ?: Vector3d(position)
        destination?.shipToWorld?.transformPosition(targetPosition)
        val targetVelocity = if (destination == null) Vector3d() else Vector3d(destination.angularVelocity)
            .cross(Vector3d(targetPosition).sub(destination.transform.positionInWorld)).add(destination.velocity)
        return FractionalFluidTransfers.Move(from, to, cellVolume(source), cellVolume(destination),
            from.dimension, position, velocity, copy, targetPosition, targetVelocity)
    }

    private fun cellVolume(ship: ServerShip?): BigDecimal {
        val scale = ship?.transform?.scaling ?: return BigDecimal.ONE
        return (BigDecimal.valueOf(scale.x()) * BigDecimal.valueOf(scale.y()) * BigDecimal.valueOf(scale.z())).abs().stripTrailingZeros()
    }
}
