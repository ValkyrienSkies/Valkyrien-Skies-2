package org.valkyrienskies.mod.gametest

import java.lang.reflect.Field
import java.lang.reflect.Method
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.yRange

// Shared by the Forge and Fabric game tests; per-loader hooks live in each loader's GameTestPlatform.kt.

/** A static ship built for a test. [origin] is the shipyard position of the block at relative (0, 0, 0). */
class TestShip(val level: ServerLevel, val ship: ServerShip, val origin: BlockPos) {
    fun shipPos(dx: Int, dy: Int, dz: Int): BlockPos = origin.offset(dx, dy, dz)

    fun toWorld(shipyard: Vec3): Vec3 {
        val v = ship.shipToWorld.transformPosition(Vector3d(shipyard.x, shipyard.y, shipyard.z))
        return Vec3(v.x, v.y, v.z)
    }

    /** World position of the centre of the ship block at [shipPos]. */
    fun worldCenter(shipPos: BlockPos): Vec3 = toWorld(Vec3.atCenterOf(shipPos))

    private var chunks: List<ChunkPos> = emptyList()

    /** Deletes the ship and releases its force-loaded chunks. */
    fun delete() {
        level.shipObjectWorld.deleteShip(ship)
        chunks.forEach { level.setChunkForced(it.x, it.z, false) }
    }

    companion object {
        /**
         * A static ship near [worldPos] built from [blocks] (relative offsets). Its chunks are force-loaded: with no
         * players around, collision lookups and block-entity ticking skip chunks that aren't fully loaded.
         */
        fun create(level: ServerLevel, worldPos: BlockPos, blocks: Map<BlockPos, BlockState>): TestShip {
            val ship = level.shipObjectWorld.createNewShipAtBlock(
                Vector3i(worldPos.x, worldPos.y, worldPos.z), false, 1.0, level.dimensionId
            )
            ship.isStatic = true
            val c = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i())
            val origin = BlockPos(c.x(), c.y(), c.z())
            val testShip = TestShip(level, ship, origin)
            testShip.chunks = (blocks.keys + BlockPos.ZERO).map { ChunkPos(origin.offset(it)) }.distinct()
            testShip.chunks.forEach { level.setChunkForced(it.x, it.z, true) }
            blocks.forEach { (rel, state) -> level.setBlock(origin.offset(rel), state, 3) }
            return testShip
        }
    }
}

/**
 * Builds a static ship near [worldPos] from [blocks] (relative offsets). After [settleTicks] it runs [body]; if
 * [thenCheck] is given it runs [thenAfter] ticks later. The ship is deleted at the end.
 */
fun GameTestHelper.withShip(
    blocks: Map<BlockPos, BlockState>,
    worldPos: BlockPos,
    settleTicks: Long = 20,
    thenAfter: Long = 2,
    thenCheck: ((TestShip) -> Unit)? = null,
    body: (TestShip) -> Unit
) {
    val testShip = TestShip.create(level, worldPos, blocks)
    fun cleanup() = testShip.delete()
    runAfterDelay(settleTicks) {
        try {
            body(testShip)
        } catch (e: Throwable) {
            cleanup()
            throw e
        }
        if (thenCheck == null) {
            cleanup()
            succeed()
        } else {
            runAfterDelay(thenAfter) {
                try {
                    thenCheck(testShip)
                    succeed()
                } finally {
                    cleanup()
                }
            }
        }
    }
}

fun GameTestHelper.check(condition: Boolean, message: () -> String) {
    if (!condition) throw GameTestAssertException(message())
}

fun <T : Entity> ServerLevel.spawnAt(type: EntityType<T>, pos: Vec3, setup: (T) -> Unit = {}): T {
    val entity = type.create(this) ?: throw GameTestAssertException("could not create $type")
    entity.moveTo(pos.x, pos.y, pos.z, 0f, 0f)
    setup(entity)
    addFreshEntity(entity)
    return entity
}

/**
 * A fake player at [pos]. [inLevel] also adds it to the level so entity searches find it; the vanilla mock player
 * can't be used because a loader's player join needs a real network channel.
 */
fun GameTestHelper.mockPlayerAt(pos: Vec3, inLevel: Boolean = false): ServerPlayer {
    val player = createFakePlayer(level)
    player.moveTo(pos.x, pos.y, pos.z, 0f, 0f)
    player.setGameMode(GameType.SURVIVAL)
    if (inLevel) level.addNewPlayer(player)
    return player
}

fun ServerPlayer.removeMock() {
    if (serverLevel().players().contains(this)) serverLevel().removePlayerImmediately(this, Entity.RemovalReason.DISCARDED)
}

/** Ship chunks only tick near real players, so block entities on test ships are ticked by hand. */
fun ServerLevel.tickBlockEntity(pos: BlockPos, times: Int) {
    val be = getBlockEntity(pos) ?: throw GameTestAssertException("no block entity at $pos")
    @Suppress("UNCHECKED_CAST")
    val ticker = be.blockState.getTicker(this, be.type as BlockEntityType<BlockEntity>)
        ?: throw GameTestAssertException("block entity at $pos has no ticker")
    repeat(times) { ticker.tick(this, pos, getBlockState(pos), be) }
}

fun box(from: BlockPos, to: BlockPos, state: BlockState): Map<BlockPos, BlockState> =
    BlockPos.betweenClosed(from, to).associate { it.immutable() to state }

fun method(owner: Class<*>, name: String, vararg params: Class<*>): Method =
    owner.getDeclaredMethod(name, *params).apply { isAccessible = true }

fun field(owner: Class<*>, name: String): Field =
    owner.getDeclaredField(name).apply { isAccessible = true }

/** Distinct non-air blocks inside [box], for failure messages. */
fun ServerLevel.blocksIn(box: net.minecraft.world.phys.AABB): String =
    BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1e-6, box.maxY - 1e-6, box.maxZ - 1e-6))
        .map { getBlockState(it) }.filter { !it.isAir }.map { it.block.descriptionId }.distinct().joinToString()


