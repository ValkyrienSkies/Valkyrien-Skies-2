package org.valkyrienskies.mod.gametest

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.util.RandomSource
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.Bee
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.PositionalRandomFactory
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

/** A RandomSource whose answers are scripted, so a mob's random choice becomes a fixed one. */
class ScriptedRandom(
    private val int: (bound: Int) -> Int = { 0 },
    private val float: () -> Float = { 0f },
    private val double: () -> Double = { 0.0 }
) : RandomSource {
    override fun fork(): RandomSource = this
    override fun forkPositional(): PositionalRandomFactory = RandomSource.create(0).forkPositional()
    override fun setSeed(seed: Long) {}
    override fun nextInt(): Int = int(Int.MAX_VALUE)
    override fun nextInt(bound: Int): Int = int(bound).coerceIn(0, bound - 1)
    override fun nextLong(): Long = 0
    override fun nextBoolean(): Boolean = false
    override fun nextFloat(): Float = float()
    override fun nextDouble(): Double = double()
    override fun nextGaussian(): Double = 0.0
}

fun Entity.scriptRandom(random: RandomSource) = field(Entity::class.java, "random").set(this, random)

// Mob behaviour driven by randomness, made deterministic with a scripted RandomSource.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipRandomMobGameTests {

    private val stone = Blocks.STONE.defaultBlockState()

    /**
     * A dark world chamber, 2x2 and two cells tall, whose south-east cell is [cell] (relative), so monster light
     * rules pass. Reinforcements are placed at a block corner, so their box reaches into the west and north cells.
     */
    private fun GameTestHelper.darkChamber(cell: BlockPos) {
        for (x in -2..1) for (y in 0..2) for (z in -2..1) {
            val p = cell.offset(x, y, z)
            val inside = x in -1..0 && z in -1..0 && y < 2
            setBlock(p, if (inside) Blocks.AIR else Blocks.STONE)
        }
    }

    @GameTest(template = TEST_AREA, batch = "zombie_reinforcement", timeoutTicks = 300)
    fun zombieReinforcementDoesNotSpawnInsideShip(helper: GameTestHelper) {
        val level = helper.level
        val oldDifficulty = level.difficulty
        level.server.setDifficulty(Difficulty.HARD, true)
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, level.server)
        // Every attempt targets the cell 7 blocks east: nextInt(34) = 0 picks 7, nextInt(3) cycles +1, 0, 0.
        fun reinforcementRandom(): ScriptedRandom {
            var axis = 0
            return ScriptedRandom(int = { bound -> if (bound == 3) intArrayOf(2, 1, 1)[axis++ % 3] else 0 })
        }
        helper.darkChamber(BlockPos(10, 2, 8))
        helper.darkChamber(BlockPos(10, 2, 3))
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(10, 2, 8))) { _ ->
            val attacker = level.spawnAt(EntityType.VILLAGER, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(1, 2, 14)))) { it.setNoAi(true) }
            fun reinforcementsNear(callerAt: BlockPos): Int {
                val caller = level.spawnAt(EntityType.ZOMBIE, Vec3.atBottomCenterOf(helper.absolutePos(callerAt))) { it.setNoAi(true) }
                caller.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE)!!.baseValue = 1.0
                caller.scriptRandom(reinforcementRandom())
                caller.hurt(level.damageSources().mobAttack(attacker), 1f)
                val spawned = level.getEntitiesOfClass(Zombie::class.java, AABB(helper.absolutePos(callerAt.offset(7, 0, 0))).inflate(1.0))
                spawned.forEach { it.discard() }
                caller.discard()
                return spawned.size
            }
            try {
                helper.check(reinforcementsNear(BlockPos(3, 2, 3)) > 0) { "zombie reinforcement didn't spawn in an open chamber; test setup is wrong" }
                helper.check(reinforcementsNear(BlockPos(3, 2, 8)) == 0) { "zombie reinforcement spawned inside a ship block" }
            } finally {
                attacker.discard()
                level.server.setDifficulty(oldDifficulty, true)
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "shulker_stay", timeoutTicks = 200)
    fun shulkerCannotOpenIntoShipBlock(helper: GameTestHelper) {
        // The ship block sits directly above a free floor cell, where an opened shell would reach.
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(10, 3, 8))) { _ ->
            val shulker = helper.level.spawnAt(EntityType.SHULKER, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(3, 2, 3)))) { it.setNoAi(true) }
            try {
                val canStayAt = method(Shulker::class.java, "canStayAt", BlockPos::class.java, Direction::class.java)
                helper.check(canStayAt.invoke(shulker, helper.absolutePos(BlockPos(4, 2, 8)), Direction.DOWN) as Boolean) {
                    "shulker couldn't sit on open floor; test setup is wrong"
                }
                helper.check(!(canStayAt.invoke(shulker, helper.absolutePos(BlockPos(10, 2, 8)), Direction.DOWN) as Boolean)) {
                    "shulker was allowed to sit where its shell would open into a ship block"
                }
            } finally {
                shulker.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "shulker_teleport", timeoutTicks = 200)
    fun shulkerDoesNotTeleportUnderShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(10, 3, 8))) { _ ->
            fun teleportTargeting(from: BlockPos, target: BlockPos): Boolean {
                val shulker = helper.level.spawnAt(EntityType.SHULKER, Vec3.atBottomCenterOf(helper.absolutePos(from)))
                // Mth.randomBetweenInclusive(-8, 8) = nextInt(17) - 8, called for x, y, z.
                val d = target.subtract(from)
                var axis = 0
                shulker.scriptRandom(ScriptedRandom(int = { bound ->
                    if (bound == 17) intArrayOf(d.x, d.y, d.z)[axis++ % 3] + 8 else 0
                }))
                try {
                    val moved = method(Shulker::class.java, "teleportSomewhere").invoke(shulker) as Boolean
                    return moved && shulker.blockPosition() == helper.absolutePos(target)
                } finally {
                    shulker.discard()
                }
            }
            helper.check(teleportTargeting(BlockPos(3, 2, 3), BlockPos(5, 2, 3))) { "shulker couldn't teleport onto open floor; test setup is wrong" }
            helper.check(!teleportTargeting(BlockPos(3, 2, 8), BlockPos(10, 2, 8))) {
                "shulker teleported to a spot where its shell would open into a ship block"
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "bee_pathfind_towards", timeoutTicks = 200)
    fun beeSteersOnlyAShortWayTowardsNearbyShipTarget(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to Blocks.BEE_NEST.defaultBlockState()), helper.absolutePos(BlockPos(10, 4, 8))) { ship ->
            val nest = ship.shipPos(0, 0, 0)
            val bee = helper.level.spawnAt(EntityType.BEE, ship.worldCenter(nest).add(-2.0, -0.5, 0.0))
            try {
                // Centred angle, mid radius: the steering offset is about as large as the search radius.
                bee.scriptRandom(ScriptedRandom(int = { bound -> bound / 2 }, float = { 0.5f }, double = { 0.5 }))
                method(Bee::class.java, "pathfindRandomlyTowards", BlockPos::class.java).invoke(bee, nest)
                val target = bee.navigation.targetPos
                    ?: throw IllegalStateException("bee picked no flight target; test setup is wrong")
                val steer = Vec3.atBottomCenterOf(target).distanceTo(bee.position())
                helper.check(steer < 3.5) { "bee 2 blocks from a ship nest steered ${"%.1f".format(steer)} blocks, as if it were far away" }
            } finally {
                bee.discard()
            }
        }
    }

    // At noon, a monster spawn check on a ship sitting inside a sealed world chamber must see the chamber's darkness,
    // not the open sky over the shipyard.
    @GameTest(template = TEST_AREA, batch = "ship_sky_light", timeoutTicks = 300)
    fun shipUnderWorldRoofIsDarkForMonsterSpawns(helper: GameTestHelper) {
        val level = helper.level
        val oldTime = level.dayTime
        level.setWeatherParameters(6000, 0, false, false)
        level.dayTime = 6000
        level.updateSkyBrightness()
        helper.darkChamber(BlockPos(10, 2, 8))
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(10, 2, 8))) { ship ->
            try {
                val random = RandomSource.create(0)
                helper.check(level.getBrightness(LightLayer.SKY, helper.absolutePos(BlockPos(10, 3, 8))) == 0) { "the chamber isn't dark; test setup is wrong" }
                helper.check(!Monster.isDarkEnoughToSpawn(level, helper.absolutePos(BlockPos(3, 2, 3)), random)) { "open ground at noon was dark enough; test setup is wrong" }
                helper.check(Monster.isDarkEnoughToSpawn(level, ship.shipPos(0, 1, 0), random)) {
                    "a ship inside a sealed world chamber was lit by the shipyard's open sky"
                }
            } finally {
                level.dayTime = oldTime
                level.updateSkyBrightness()
            }
        }
    }
}
