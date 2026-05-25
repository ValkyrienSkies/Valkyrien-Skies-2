package org.valkyrienskies.mod.common.util

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.joml.primitives.AABBi
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.internal.collision.VsiConvexPolygonc
import org.valkyrienskies.core.util.extend
import org.valkyrienskies.core.util.toAABBd
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.unloadedShips
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.util.BugFixUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

object EntityShipCollisionUtils {

    /**
     * Tracks recently-spawned ships by their ID and the tick they were created.
     * Ships in this grace period are excluded from unloaded-ship collision checks
     * to prevent freezing the player when a new ship is assembled nearby but its
     * chunks haven't loaded yet.
     */
    private val recentlySpawnedShips = ConcurrentHashMap<ShipId, Long>()
    private const val SPAWN_GRACE_PERIOD_TICKS = 100L // ~5 seconds
    private val playerUnloadedShipBlockStartTicks = ConcurrentHashMap<Long, Long>()
    private val playerClientSyncBlockStartTicks = ConcurrentHashMap<Int, Long>()
    private const val PLAYER_UNLOADED_SHIP_BLOCK_TIMEOUT_TICKS = 200L // ~10 seconds

    private const val PLAYER_JOIN_GRACE_TICKS = 200L // ~10 seconds

    @JvmStatic
    fun markShipAsRecentlySpawned(shipId: ShipId, currentTick: Long) {
        recentlySpawnedShips[shipId] = currentTick
    }

    @JvmStatic
    fun cleanupExpiredGracePeriods(currentTick: Long) {
        recentlySpawnedShips.entries.removeIf { (_, spawnTick) ->
            currentTick - spawnTick > SPAWN_GRACE_PERIOD_TICKS
        }
    }

    @JvmStatic
    fun isInSpawnGracePeriod(shipId: ShipId): Boolean {
        return recentlySpawnedShips.containsKey(shipId)
    }

    private fun playerShipBlockKey(entity: Entity, shipId: ShipId): Long {
        val uuid = entity.uuid
        return uuid.leastSignificantBits xor java.lang.Long.rotateLeft(uuid.mostSignificantBits, 1) xor shipId
    }

    private fun shouldBlockPlayerForUnloadedShip(entity: Entity, ship: Ship, currentTick: Long): Boolean {
        if (entity !is Player) {
            return true
        }
        val key = playerShipBlockKey(entity, ship.id)
        val firstBlockedTick = playerUnloadedShipBlockStartTicks.putIfAbsent(key, currentTick) ?: currentTick
        return currentTick - firstBlockedTick <= PLAYER_UNLOADED_SHIP_BLOCK_TIMEOUT_TICKS
    }

    private fun shouldBlockPlayerForClientShipSync(entity: Entity, currentTick: Long): Boolean {
        if (entity !is Player) {
            return true
        }
        val firstBlockedTick = playerClientSyncBlockStartTicks.putIfAbsent(entity.id, currentTick) ?: currentTick
        return currentTick - firstBlockedTick <= PLAYER_UNLOADED_SHIP_BLOCK_TIMEOUT_TICKS
    }

    private const val PARTICLE_COLLISION_BOX_EXPANSION = 0.00390625 //1.0 / 256.0

    private val collider = vsCore.entityPolygonCollider

    private fun getShipyardChunkAABBAround(ship: Ship): AABBi {
        val box = AABBi()
        // Since we don't know how big the ship is vertically we'll just have to trust the shipAABB and add some margin of error.
        val minY = (ship.shipAABB?.minY() ?: Mth.floor(ship.transform.position.y())) - 16
        val maxY = (ship.shipAABB?.maxY() ?: Mth.ceil(ship.transform.position.y())) + 16
        ship.activeChunksSet.forEach { x, z ->
            val minX = SectionPos.sectionToBlockCoord(x)
            val minZ = SectionPos.sectionToBlockCoord(z)
            val maxX = SectionPos.sectionToBlockCoord(x, 15)
            val maxZ = SectionPos.sectionToBlockCoord(z, 15)
            box.union(minX, minY, minZ).union(maxX, maxY, maxZ)
        }
        return box
    }

    private fun getAllShipsIntersectingEvenIfNotYetFullyLoaded(level: Level, aabb: AABBd): Stream<Ship> {
        // shipAABB and worldAABB are sometimes too small when ship was just loaded for the first time.
        // To circumvent this, we use activeChunksSet to find a rougher bounding box which should always contain the entire ship.
        return level.unloadedShips.stream().filter { ship ->
            ship.chunkClaimDimension == level.dimensionId &&
            getShipyardChunkAABBAround(ship).toAABBd(AABBd()).transform(ship.shipToWorld).intersectsAABB(aabb)
        }
    }

    @JvmStatic
    fun isCollidingWithUnloadedShips(entity: Entity): Boolean {
        val level = entity.level()

        if (level is ServerLevel || (level.isClientSide && level is ClientLevel)) {
            if (entity is Player && entity.tickCount < PLAYER_JOIN_GRACE_TICKS) {
                return false
            }
            if (level.isClientSide && level is ClientLevel && !level.shipObjectWorld.isSyncedWithServer) {
                return shouldBlockPlayerForClientShipSync(entity, level.gameTime)
            } else if (entity is Player) {
                playerClientSyncBlockStartTicks.remove(entity.id)
            }
            if (level.unloadedShips.isEmpty()) {
                return false
            }

            val aabb = entity.boundingBox.toJOML()
            val currentTick = level.gameTime
            return getAllShipsIntersectingEvenIfNotYetFullyLoaded(level, aabb)
                .allMatch { ship ->
                    // Skip collision check for recently-spawned ships whose chunks are still
                    // loading. Without this, spawning a new ship near a player would freeze
                    // them because isCollidingWithUnloadedShips returns true (the new ship's
                    // chunks haven't loaded yet), which cancels all entity movement.
                    if (isInSpawnGracePeriod(ship.id)) {
                        return@allMatch true // pretend it's loaded → don't block movement
                    }
                    val aabbInShip = AABBd(aabb).transform(ship.worldToShip)
                    val chunksLoaded = areAllChunksLoaded(ship, aabbInShip, level)
                    if (chunksLoaded) {
                        playerUnloadedShipBlockStartTicks.remove(playerShipBlockKey(entity, ship.id))
                        return@allMatch true
                    }
                    !shouldBlockPlayerForUnloadedShip(entity, ship, currentTick)
                }
                .not()
        }

        return false
    }

    private fun areAllChunksLoaded(ship: Ship, aABB: AABBdc, level: Level): Boolean {
        val minX = (Mth.floor(aABB.minX() - 1.0E-7) - 1) shr 4
        val maxX = (Mth.floor(aABB.maxX() + 1.0E-7) + 1) shr 4
        val minZ = (Mth.floor(aABB.minZ() - 1.0E-7) - 1) shr 4
        val maxZ = (Mth.floor(aABB.maxZ() + 1.0E-7) + 1) shr 4

        for (chunkX in minX..maxX) {
            for (chunkZ in minZ..maxZ) {
                if (ship.activeChunksSet.contains(chunkX, chunkZ) &&
                    level.getChunkForCollisions(chunkX, chunkZ) == null
                ) {
                    return false
                }
            }
        }

        return true
    }

    @JvmStatic
    fun overlapsAnyActiveChunk(ship: Ship, aABB: AABBdc): Boolean {
        val minX = (Mth.floor(aABB.minX() - 1.0E-7) - 1) shr 4
        val maxX = (Mth.floor(aABB.maxX() + 1.0E-7) + 1) shr 4
        val minZ = (Mth.floor(aABB.minZ() - 1.0E-7) - 1) shr 4
        val maxZ = (Mth.floor(aABB.maxZ() + 1.0E-7) + 1) shr 4

        for (chunkX in minX..maxX) {
            for (chunkZ in minZ..maxZ) {
                if (ship.activeChunksSet.contains(chunkX, chunkZ)) {
                    return true
                }
            }
        }

        return false
    }

    @JvmStatic
    fun mayShipIntersectLocalAabb(ship: Ship, aABB: AABBdc): Boolean {
        val shipAabb = ship.shipAABB ?: return false
        return shipAabb.minX() <= aABB.maxX() &&
            shipAabb.maxX() >= aABB.minX() &&
            shipAabb.minY() <= aABB.maxY() &&
            shipAabb.maxY() >= aABB.minY() &&
            shipAabb.minZ() <= aABB.maxZ() &&
            shipAabb.maxZ() >= aABB.minZ() &&
            overlapsAnyActiveChunk(ship, aABB)
    }

    /**
     * @return [movement] modified such that the entity collides with ships.
     */
    fun adjustEntityMovementForShipCollisions(
        entity: Entity?,
        movement: Vec3,
        entityBoundingBox: AABB,
        world: Level
    ): Vec3 {
        // Inflate the bounding box more for players than other entities, to give players a better collision result.
        // Note that this increases the cost of doing collision, so we only do it for the players
        val inflation = if (entity is Player) 0.5 else 0.1
        val stepHeight: Double = entity?.maxUpStep()?.toDouble() ?: 0.0
        // Add [max(stepHeight - inflation, 0.0)] to search for polygons we might collide with while stepping

        // This part was slightly changed to inflate the bounding box in y-axis and adjust the center point. - Bunting_chj
        val collidingShipPolygons =
            getShipPolygonsCollidingWithEntity(
                entity, movement,
                entityBoundingBox.inflate(inflation, inflation + stepHeight / 2, inflation)
                    .move(0.0, stepHeight / 2, 0.0),
                world
            )

        if (collidingShipPolygons.isEmpty()) {
            return movement
        }

        val collisionBoundingBox = if (entity == null) {
            entityBoundingBox.inflate(PARTICLE_COLLISION_BOX_EXPANSION)
        } else {
            entityBoundingBox
        }

        val (newMovement, shipCollidingWith) = collider.adjustEntityMovementForPolygonCollisions(
            movement.toJOML(), collisionBoundingBox.toJOML(), stepHeight, collidingShipPolygons, entity?.onGround() ?: false
        )
        if (entity != null) {
            val standingOnShip = entity.level().getLoadedShipManagingPos(entity.onPos)
            if (shipCollidingWith != null && standingOnShip != null && standingOnShip.id == shipCollidingWith) {
                // Update the [IEntity.lastShipStoodOn]
                (entity as IEntityDraggingInformationProvider).draggingInformation.lastShipStoodOn = shipCollidingWith
                for (entityRiding in entity.indirectPassengers) {
                    (entityRiding as IEntityDraggingInformationProvider).draggingInformation.lastShipStoodOn = shipCollidingWith
                }
            }
        }
        return newMovement.toMinecraft()
    }

    @JvmStatic
    fun adjustEntityMovementForShipyardEntityCollisions(
        entity: Entity?,
        movement: Vec3,
        entityBoundingBox: AABB,
        world: Level
    ): Vec3 {
        val sweptBox = entityBoundingBox.expandTowards(movement).inflate(1.0e-7)
        val candidates = world.getEntities(entity, sweptBox) { other ->
            if (entity != null && !entity.canCollideWith(other)) return@getEntities false
            other.canBeCollidedWith()
        }
        if (candidates.isEmpty()) return movement

        val candidatesByShip = LinkedHashMap<Ship, MutableList<Entity>>()
        for (candidate in candidates) {
            val ship = world.getLoadedShipManagingPos(candidate.blockPosition()) ?: continue
            candidatesByShip.getOrPut(ship) { ArrayList() }.add(candidate)
        }
        if (candidatesByShip.isEmpty()) return movement

        var currentMovement = movement
        for ((ship, candList) in candidatesByShip) {
            currentMovement = clipMovementInShipLocalFrame(
                ship, candList, entityBoundingBox, currentMovement
            )
        }
        return currentMovement
    }

    private fun clipMovementInShipLocalFrame(
        ship: Ship,
        candidates: List<Entity>,
        playerWorldAabb: AABB,
        worldMovement: Vec3
    ): Vec3 {
        var playerLocal = AABBd(playerWorldAabb.toJOML()).transform(ship.worldToShip).toMinecraft()
        val movLocal = ship.worldToShip.transformDirection(worldMovement.toJOML(), Vector3d())
        var clippedX = movLocal.x
        var clippedY = movLocal.y
        var clippedZ = movLocal.z

        var combined: VoxelShape = Shapes.empty()
        for (candidate in candidates) {
            combined = Shapes.or(combined, Shapes.create(candidate.boundingBox))
        }

        if (clippedY != 0.0) {
            clippedY = combined.collide(Direction.Axis.Y, playerLocal, clippedY)
            if (clippedY != 0.0) playerLocal = playerLocal.move(0.0, clippedY, 0.0)
        }
        val zDominant = Math.abs(clippedX) < Math.abs(clippedZ)
        if (zDominant && clippedZ != 0.0) {
            clippedZ = combined.collide(Direction.Axis.Z, playerLocal, clippedZ)
            playerLocal = playerLocal.move(0.0, 0.0, clippedZ)
        }
        if (clippedX != 0.0) {
            clippedX = combined.collide(Direction.Axis.X, playerLocal, clippedX)
            if (!zDominant && clippedX != 0.0) playerLocal = playerLocal.move(clippedX, 0.0, 0.0)
        }
        if (!zDominant && clippedZ != 0.0) {
            clippedZ = combined.collide(Direction.Axis.Z, playerLocal, clippedZ)
        }

        val clippedWorld = ship.shipToWorld.transformDirection(
            Vector3d(clippedX, clippedY, clippedZ), Vector3d())
        return Vec3(clippedWorld.x, clippedWorld.y, clippedWorld.z)
    }

    fun getShipPolygonsCollidingWithEntity(
        entity: Entity?,
        movement: Vec3,
        entityBoundingBox: AABB,
        world: Level
    ): List<VsiConvexPolygonc> {
        if (world.shipObjectWorld.loadedShips.isEmpty()) {
            return emptyList()
        }

        val entityBoxWithMovement = entityBoundingBox.expandTowards(movement)
        val collidingPolygons: MutableList<VsiConvexPolygonc> = ArrayList()
        val entityBoundingBoxExtended = entityBoundingBox.toJOML().extend(movement.toJOML())
        val entityBoxWithMovementJoml = entityBoxWithMovement.toJOML()
        val entityBoundingBoxInShipCoordinates = AABBd()
        for (shipObject in world.shipObjectWorld.loadedShips.getIntersecting(entityBoundingBoxExtended, world.dimensionId)) {
            val shipTransform = shipObject.transform
            entityBoxWithMovementJoml.transform(shipTransform.worldToShip, entityBoundingBoxInShipCoordinates)
            if (BugFixUtil.isCollisionBoxTooBig(entityBoundingBoxInShipCoordinates.toMinecraft())) {
                // Box too large, skip it
                continue
            }
            if (!mayShipIntersectLocalAabb(shipObject, entityBoundingBoxInShipCoordinates)) {
                continue
            }
            val entityPolyInShipCoordinates: VsiConvexPolygonc = collider.createPolygonFromAABB(
                entityBoxWithMovementJoml,
                shipTransform.worldToShip
            )
            val shipBlockCollisionStream =
                world.getBlockCollisions(entity, entityBoundingBoxInShipCoordinates.toMinecraft())
            shipBlockCollisionStream.forEach { voxelShape: VoxelShape ->
                voxelShape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                    val shipPolygon: VsiConvexPolygonc = vsCore.entityPolygonCollider.createPolygonFromAABB(
                        AABBd(minX, minY, minZ, maxX, maxY, maxZ),
                        shipTransform.shipToWorld,
                        shipObject.id
                    )
                    collidingPolygons.add(shipPolygon)
                }
            }
        }
        return collidingPolygons
    }
}
