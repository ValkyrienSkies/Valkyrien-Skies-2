package org.valkyrienskies.mod.common.mob_spawning

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.SpawnGroupData
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.level.NaturalSpawner
import net.minecraft.world.level.NaturalSpawner.SpawnState
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.MobSpawnSettings
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.mixin.accessors.world.level.NaturalSpawnerInvoker
import org.valkyrienskies.mod.mixin.accessors.world.level.NaturalSpawnerSpawnStateInvoker

/**
 * Parallel spawn pass invoked from `NaturalSpawnerMixin`'s TAIL inject after vanilla
 * `NaturalSpawner.spawnForChunk` finishes. For each ship intersecting the world chunk's
 * vertical column, runs vanilla's `spawnCategoryForPosition` flow in the ship's local cells
 * with biome / structure / difficulty drawn from the ship's world-rendered pos (shipyard
 * chunks aren't in the ticket pipeline; vanilla's `getMobsAt` crashes there). Mob caps are
 * shared with vanilla via `SpawnState`; biome reads project via `ShipSpawnFinalizeContext`.
 */
object ShipNaturalSpawner {

    private val SPAWN_CATEGORIES: Array<MobCategory> =
        MobCategory.values().filter { it != MobCategory.MISC }.toTypedArray()

    @JvmStatic
    fun spawnForShipsIn(
        level: ServerLevel,
        chunk: LevelChunk,
        spawnState: SpawnState,
        spawnFriendlies: Boolean,
        spawnMonsters: Boolean,
        spawnPassive: Boolean
    ) {
        if (!VSGameConfig.SERVER.allowMobSpawns) return

        val invoker = spawnState as NaturalSpawnerSpawnStateInvoker
        val chunkPos = chunk.pos

        // Perf early-out (algorithmic, independent of the throttle below): `canSpawnForCategory`
        // depends only on (category, chunkPos) — never on a ship — and is monotonically non-increasing
        // within this call, because the only thing that mutates the SpawnState here is `afterSpawn`,
        // which raises mob counts toward the cap; nothing lowers them. Therefore if NO gated category
        // is under its cap at chunk entry, the per-ship loop below could not spawn anything either.
        // Bail out BEFORE querying the ship spatial index and iterating every intersecting ship. In a
        // dense / soaked scene (e.g. 1800 piled ships with caps full) this is the common path and
        // collapses the whole pass to at most ~7 cheap cap checks per chunk — no spatial query, no ship
        // iteration. Strictly behavior-identical: the inner loop still performs the same per-ship checks.
        var anyCategorySpawnable = false
        for (category in SPAWN_CATEGORIES) {
            if (categoryGate(category, spawnFriendlies, spawnMonsters, spawnPassive) &&
                invoker.`vs$canSpawnForCategory`(category, chunkPos)
            ) {
                anyCategorySpawnable = true
                break
            }
        }
        if (!anyCategorySpawnable) return

        val column = AABBd(
            chunkPos.minBlockX.toDouble(),
            level.minBuildHeight.toDouble(),
            chunkPos.minBlockZ.toDouble(),
            chunkPos.maxBlockX + 1.0,
            level.maxBuildHeight.toDouble(),
            chunkPos.maxBlockZ + 1.0
        )

        // Perf: optionally throttle the per-ship spawn pass. For a large ship carrying a whole
        // population (a village on a ship), running the full spawn flow for every category every
        // spawn cycle is a notable per-tick server cost. With an interval > 1 we stagger ships by
        // id so different ships fire on different ticks (smoothing the spike) and each ship's pass
        // runs only every Nth cycle. interval == 1 is exact vanilla-parity behavior.
        val spawnInterval = VSGameConfig.SERVER.Performance.shipMobSpawnIntervalTicks
        val gameTime = level.gameTime

        for (ship in level.getShipsIntersecting(column)) {
            if (spawnInterval > 1 &&
                Math.floorMod(gameTime + ship.id, spawnInterval.toLong()) != 0L
            ) continue
            for (category in SPAWN_CATEGORIES) {
                if (!categoryGate(category, spawnFriendlies, spawnMonsters, spawnPassive)) continue
                if (!invoker.`vs$canSpawnForCategory`(category, chunkPos)) continue
                trySpawnOnShip(category, ship, level, chunk, spawnState)
            }
        }
    }

    private fun categoryGate(
        category: MobCategory,
        spawnFriendlies: Boolean, spawnMonsters: Boolean, spawnPassive: Boolean
    ): Boolean {
        return (spawnFriendlies || !category.isFriendly)
            && (spawnMonsters || category.isFriendly)
            && (spawnPassive || !category.isPersistent)
    }

    private fun trySpawnOnShip(
        category: MobCategory,
        ship: Ship,
        level: ServerLevel,
        chunk: LevelChunk,
        spawnState: SpawnState
    ) {
        val random = level.random

        val shipAABB = ship.shipAABB
        if (shipAABB == null || !shipAABB.isValid) return

        val worldX0 = chunk.pos.minBlockX + random.nextInt(16)
        val worldZ0 = chunk.pos.minBlockZ + random.nextInt(16)
        val midY = (shipAABB.minY() + shipAABB.maxY()) / 2.0
        val shipyardSeed = ship.transform.worldToShip.transformPosition(
            worldX0 + 0.5, midY, worldZ0 + 0.5, Vector3d()
        )
        val shipyardX = Math.floor(shipyardSeed.x).toInt()
        val shipyardZ = Math.floor(shipyardSeed.z).toInt()

        if (shipyardX < shipAABB.minX() || shipyardX > shipAABB.maxX()
            || shipyardZ < shipAABB.minZ() || shipyardZ > shipAABB.maxZ()
        ) return

        // Vanilla parity: pick Y across the FULL column [minBuildHeight, surfaceY+1] like
        // `getRandomPosWithin`. Restricting to the ship's narrow Y range would 5-20× the
        // per-attempt success rate vs vanilla's wide-Y throw-away spread.
        val surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, shipyardX, shipyardZ)
        val yLimit = if (surfaceY > level.minBuildHeight) surfaceY + 1 else shipAABB.maxY() + 1
        val pickedY = Mth.randomBetweenInclusive(random, level.minBuildHeight, yLimit)
        if (pickedY < level.minBuildHeight + 1) return
        val initialPos = BlockPos(shipyardX, pickedY, shipyardZ)

        if (level.getBlockState(initialPos).isRedstoneConductor(level, initialPos)) return

        val structureManager = level.structureManager()
        val generator = level.chunkSource.generator
        val mutable = BlockPos.MutableBlockPos()
        val invoker = spawnState as NaturalSpawnerSpawnStateInvoker
        var totalSpawned = 0

        // Push ship context so vanilla's biome reads project to the ship's world-rendered biome.
        ShipSpawnFinalizeContext.push(ship)
        try {
            for (attempt in 0 until 3) {
                var spawnX = initialPos.x
                var spawnZ = initialPos.z
                var spawnerData: MobSpawnSettings.SpawnerData? = null
                var spawnGroupData: SpawnGroupData? = null
                var groupSize = Mth.ceil(random.nextFloat() * 4.0f)
                var spawnedInGroup = 0

                var j = 0
                groupLoop@ while (j < groupSize) {
                    j++
                    spawnX += random.nextInt(6) - random.nextInt(6)
                    spawnZ += random.nextInt(6) - random.nextInt(6)
                    mutable.set(spawnX, pickedY, spawnZ)

                    val cx = spawnX + 0.5
                    val cy = pickedY.toDouble()
                    val cz = spawnZ + 0.5

                    // Project shipyard pos -> world for structure resolution AND difficulty
                    // (so DifficultyInstance reflects the ship's render location, not shipyard-zero inhabited time).
                    val rendered = ship.transform.shipToWorld.transformPosition(cx, cy, cz, Vector3d())
                    val worldPos = BlockPos.containing(rendered.x, rendered.y, rendered.z)

                    val player = level.getNearestPlayer(cx, cy, cz, -1.0, false) ?: continue@groupLoop
                    val playerDistSqr = player.distanceToSqr(cx, cy, cz)

                    if (!isRightDistanceShipAware(level, ship, mutable, playerDistSqr)) continue@groupLoop

                    if (spawnerData == null) {
                        val biome = level.getBiome(mutable)
                        val data = pickRandomSpawnMobAtShipAware(
                            level, structureManager, generator, category, biome, worldPos, random
                        ) ?: break@groupLoop
                        spawnerData = data
                        groupSize = spawnerData.minCount +
                            random.nextInt(1 + spawnerData.maxCount - spawnerData.minCount)
                    }

                    if (!isValidSpawnPositionForTypeShipAware(level, spawnerData, mutable, playerDistSqr)) continue@groupLoop

                    if (!invoker.`vs$canSpawn`(spawnerData.type, mutable, chunk)) continue@groupLoop

                    val mob = NaturalSpawnerInvoker.`vs$getMobForSpawn`(level, spawnerData.type) ?: return
                    mob.moveTo(cx, cy, cz, random.nextFloat() * 360.0f, 0.0f)

                    if (!NaturalSpawnerInvoker.`vs$isValidPositionForMob`(level, mob, playerDistSqr)) {
                        mob.discard()
                        continue@groupLoop
                    }

                    spawnGroupData = mob.finalizeSpawn(
                        level, level.getCurrentDifficultyAt(worldPos),
                        MobSpawnType.NATURAL, spawnGroupData, null
                    )
                    spawnedInGroup++
                    totalSpawned++
                    level.addFreshEntityWithPassengers(mob)
                    invoker.`vs$afterSpawn`(mob, chunk)

                    if (totalSpawned >= mob.maxSpawnClusterSize) return
                    if (mob.isMaxGroupSizeReached(spawnedInGroup)) break@groupLoop
                }
            }
        } finally {
            ShipSpawnFinalizeContext.pop()
        }
    }

    /**
     * Vanilla `getRandomSpawnMobAt` + `mobsAt` chain with structure-dependent calls
     * (`isInNetherFortressBounds`, `getMobsAt`) on the ship's world-rendered pos to dodge
     * the shipyard-chunk ticket-pipeline crash.
     */
    private fun pickRandomSpawnMobAtShipAware(
        level: ServerLevel,
        structureManager: StructureManager,
        generator: ChunkGenerator,
        category: MobCategory,
        biome: Holder<Biome>,
        worldPos: BlockPos,
        random: RandomSource
    ): MobSpawnSettings.SpawnerData? {
        if (category == MobCategory.WATER_AMBIENT
            && biome.`is`(BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS)
            && random.nextFloat() < 0.98f
        ) return null
        val list = if (NaturalSpawner.isInNetherFortressBounds(worldPos, level, category, structureManager)) {
            NetherFortressStructure.FORTRESS_ENEMIES
        } else {
            generator.getMobsAt(biome, structureManager, category, worldPos)
        }
        return list.getRandom(random).orElse(null)
    }

    /**
     * Vanilla `isValidSpawnPostitionForType` minus the `canSpawnMobAt` re-check (which
     * re-resolves the spawn list and crashes on shipyard pos via the same structure path).
     * Re-check is redundant — `spawnerData` was already picked from a freshly-resolved list.
     */
    private fun isValidSpawnPositionForTypeShipAware(
        level: ServerLevel,
        spawnerData: MobSpawnSettings.SpawnerData,
        pos: BlockPos.MutableBlockPos,
        playerDistSqr: Double
    ): Boolean {
        val type = spawnerData.type
        if (type.category == MobCategory.MISC) return false
        val despawnDist = type.category.despawnDistance.toDouble()
        if (playerDistSqr > despawnDist * despawnDist && !type.canSpawnFarFromPlayer()) return false
        val placementType = SpawnPlacements.getPlacementType(type)
        if (!NaturalSpawner.isSpawnPositionOk(placementType, level, pos, type)) return false
        if (!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, level.random)) return false
        val aabb = type.getAABB(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        return level.noCollision(aabb)
    }

    /**
     * Vanilla `isRightDistanceToPlayerAndSpawnPoint` rewritten for ship spawns. Uses the
     * ship's world-rendered pos for the world-spawn-radius check. Drops vanilla's
     * `isNaturalSpawningAllowed(pos)` fall-through — that delegates to entity-section loaded
     * state, false for shipyard chunks; ship-loaded state is implied by reaching this code.
     */
    private fun isRightDistanceShipAware(
        level: ServerLevel, ship: Ship, pos: BlockPos.MutableBlockPos, distSqr: Double
    ): Boolean {
        if (distSqr <= 576.0) return false
        val worldRendered = ship.transform.shipToWorld.transformPosition(
            pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, Vector3d()
        )
        val renderedVec = Vec3(worldRendered.x, worldRendered.y, worldRendered.z)
        if (level.sharedSpawnPos.closerToCenterThan(renderedVec, 24.0)) return false
        return true
    }
}
