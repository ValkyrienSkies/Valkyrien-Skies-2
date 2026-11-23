package org.valkyrienskies.mod.gametest

import java.util.function.Function
import java.util.function.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.QuartPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.UniformInt
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.InsideBrownianWalk
import net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos
import net.minecraft.world.entity.ai.goal.BreakDoorGoal
import net.minecraft.world.entity.ai.goal.DoorInteractGoal
import net.minecraft.world.entity.ai.goal.FleeSunGoal
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.animal.Bee
import net.minecraft.world.entity.animal.Fox
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Evoker
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.monster.ZombieVillager
import net.minecraft.world.entity.projectile.EvokerFangs
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Mob goals and behaviours that read ship blocks, treat ship blocks as roof, or collide with them.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipMobGameTests {

    private val stone = Blocks.STONE.defaultBlockState()

    private class ReachProbe(mob: PathfinderMob) : MoveToBlockGoal(mob, 1.0, 4) {
        override fun isValidTarget(level: LevelReader, pos: BlockPos) = true
        fun aimAt(pos: BlockPos) { blockPos = pos }
        val reached get() = isReachedTarget
    }

    private class ShelterProbe(mob: PathfinderMob) : FleeSunGoal(mob, 1.0) {
        var searched = false
        override fun setWantedPos(): Boolean {
            searched = true
            return false
        }
    }

    private fun GameTestHelper.daytime() {
        level.dayTime = 6000
        level.setWeatherParameters(6000, 0, false, false)
    }

    private fun innerGoal(name: String, vararg args: Any): Any {
        val cls = Class.forName("net.minecraft.world.entity.$name")
        val ctor = cls.declaredConstructors.first { it.parameterCount == args.size }
        ctor.isAccessible = true
        return ctor.newInstance(*args)
    }

    @GameTest(template = TEST_AREA, batch = "break_door", timeoutTicks = 200)
    fun zombieKeepsBreakingAdjacentShipDoor(helper: GameTestHelper) {
        val blocks = mapOf(
            BlockPos(0, -1, 0) to stone,
            BlockPos.ZERO to Blocks.OAK_DOOR.defaultBlockState(),
            BlockPos(0, 1, 0) to Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
        )
        helper.withShip(blocks, helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val door = ship.shipPos(0, 0, 0)
            val zombie = helper.level.spawnAt(EntityType.ZOMBIE, ship.worldCenter(door).add(1.0, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                val goal = BreakDoorGoal(zombie, Predicate { true })
                field(DoorInteractGoal::class.java, "doorPos").set(goal, door)
                helper.check(goal.canContinueToUse()) { "zombie 1 block from a ship door stopped breaking it" }
            } finally {
                zombie.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "move_to_block", timeoutTicks = 200)
    fun mobOnShipBlockHasReachedIt(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val block = ship.shipPos(0, 0, 0)
            val zombie = helper.level.spawnAt(EntityType.ZOMBIE, ship.worldCenter(block).add(0.3, 0.5, 0.0)) { it.setNoAi(true) }
            try {
                val goal = ReachProbe(zombie)
                goal.aimAt(block)
                goal.tick()
                helper.check(goal.reached) { "zombie standing on a ship block hadn't reached it" }
            } finally {
                zombie.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "bee_enter_hive", timeoutTicks = 200)
    fun beeNextToShipHiveWantsToEnter(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to Blocks.BEEHIVE.defaultBlockState()), helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val hive = ship.shipPos(0, 0, 0)
            val bee = helper.level.spawnAt(EntityType.BEE, ship.worldCenter(hive).add(1.0, 0.0, 0.0)) { it.setNoAi(true) }
            try {
                field(Bee::class.java, "hivePos").set(bee, hive)
                method(Bee::class.java, "setHasNectar", Boolean::class.javaPrimitiveType!!).invoke(bee, true)
                val goal = innerGoal("animal.Bee\$BeeEnterHiveGoal", bee)
                helper.check(method(goal.javaClass, "canBeeUse").invoke(goal) as Boolean) {
                    "bee 1 block from its ship hive didn't want to enter"
                }
            } finally {
                bee.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "fox_shelter", timeoutTicks = 200)
    fun foxUnderShipRoofHasShelter(helper: GameTestHelper) {
        val roof = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), stone)
        helper.setBlock(BlockPos(8, 1, 8), Blocks.GRASS_BLOCK)
        helper.withShip(roof, helper.absolutePos(BlockPos(8, 6, 8))) { _ ->
            val fox = helper.level.spawnAt(EntityType.FOX, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(8, 2, 8)))) { it.setNoAi(true) }
            try {
                val goal = innerGoal("animal.Fox\$SleepGoal", fox)
                val hasShelter = method(Class.forName("net.minecraft.world.entity.animal.Fox\$FoxBehaviorGoal"), "hasShelter")
                helper.check(hasShelter.invoke(goal) as Boolean) { "fox under a ship roof had no shelter" }
            } finally {
                fox.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "flee_sun", timeoutTicks = 200)
    fun burningMobUnderShipRoofDoesNotSeekShelter(helper: GameTestHelper) {
        helper.daytime()
        val roof = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), stone)
        helper.withShip(roof, helper.absolutePos(BlockPos(11, 7, 8))) { ship ->
            val under = helper.level.spawnAt(EntityType.ZOMBIE, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -4.0, 0.0)) { it.setNoAi(true) }
            val open = helper.level.spawnAt(EntityType.ZOMBIE, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) { it.setNoAi(true) }
            try {
                val underProbe = ShelterProbe(under)
                val openProbe = ShelterProbe(open)
                listOf(under, open).forEach { it.remainingFireTicks = 200 }
                underProbe.canUse()
                openProbe.canUse()
                helper.check(openProbe.searched) {
                    "burning zombie in open daylight didn't seek shelter; test setup is wrong (day=${helper.level.isDay}, " +
                        "fire=${open.isOnFire}, sky=${helper.level.canSeeSky(open.blockPosition())}, target=${open.target})"
                }
                helper.check(!underProbe.searched) { "burning zombie under a ship roof went looking for shelter" }
            } finally {
                under.discard()
                open.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "fox_seek_shelter", timeoutTicks = 200)
    fun foxUnderShipRoofDoesNotSeekShelter(helper: GameTestHelper) {
        helper.daytime()
        val roof = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), stone)
        helper.withShip(roof, helper.absolutePos(BlockPos(11, 7, 8))) { ship ->
            val under = helper.level.spawnAt(EntityType.FOX, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -4.0, 0.0)) { it.setNoAi(true) }
            val open = helper.level.spawnAt(EntityType.FOX, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) { it.setNoAi(true) }
            try {
                // Whether the goal went looking for a hiding spot shows up as consumed randomness.
                fun searched(fox: Fox): Boolean {
                    field(Entity::class.java, "random").set(fox, RandomSource.create(42))
                    val goal = innerGoal("animal.Fox\$SeekShelterGoal", fox, 1.25)
                    field(goal.javaClass, "interval").setInt(goal, 0)
                    method(goal.javaClass, "canUse").invoke(goal)
                    return fox.random.nextLong() != RandomSource.create(42).nextLong()
                }
                helper.check(searched(open)) {
                    "fox in open daylight didn't seek shelter; test setup is wrong (day=${helper.level.isDay}, " +
                        "sky=${helper.level.canSeeSky(open.blockPosition())}, sleeping=${open.isSleeping})"
                }
                helper.check(!searched(under)) { "fox under a ship roof went looking for shelter" }
            } finally {
                under.discard()
                open.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "enderman_daylight", timeoutTicks = 200)
    fun endermanUnderShipRoofDoesNotFleeDaylight(helper: GameTestHelper) {
        helper.daytime()
        val roof = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), stone)
        helper.withShip(roof, helper.absolutePos(BlockPos(11, 8, 8))) { ship ->
            val under = helper.level.spawnAt(EntityType.ENDERMAN, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -5.0, 0.0)) { it.setNoAi(true) }
            val open = helper.level.spawnAt(EntityType.ENDERMAN, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) { it.setNoAi(true) }
            try {
                val step = method(EnderMan::class.java, "customServerAiStep")
                fun teleportsInDaylight(e: EnderMan): Boolean {
                    val start = e.position()
                    e.tickCount = 100000
                    repeat(3000) {
                        step.invoke(e)
                        if (e.position() != start) return true
                    }
                    return false
                }
                helper.check(teleportsInDaylight(open)) {
                    "enderman in open daylight never teleported away; test setup is wrong (day=${helper.level.isDay}, " +
                        "light=${open.lightLevelDependentMagicValue}, sky=${helper.level.canSeeSky(open.blockPosition())})"
                }
                helper.check(!teleportsInDaylight(under)) { "enderman under a ship roof teleported away from daylight" }
            } finally {
                under.discard()
                open.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "brownian_walk", timeoutTicks = 200)
    fun mobInsideShipBlockDoesNotPickShelteredWalk(helper: GameTestHelper) {
        // World roof so both villagers are sheltered regardless of ships.
        for (x in 5..12) for (z in 5..12) helper.setBlock(BlockPos(x, 5, z), Blocks.STONE)
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(7, 2, 7))) { ship ->
            val inside = helper.level.spawnAt(EntityType.VILLAGER, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -0.5, 0.0)) { it.setNoAi(true) }
            val free = helper.level.spawnAt(EntityType.VILLAGER, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(10, 2, 10)))) { it.setNoAi(true) }
            try {
                val walk = InsideBrownianWalk.create(0.5f)
                walk.tryStart(helper.level, free, helper.level.gameTime)
                walk.tryStart(helper.level, inside, helper.level.gameTime)
                helper.check(free.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                    "sheltered villager picked no walk; test setup is wrong (sky=${helper.level.canSeeSky(free.blockPosition())}, " +
                        "free=${helper.level.noCollision(free)})"
                }
                helper.check(!inside.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) { "villager stuck inside a ship block still picked a walk" }
            } finally {
                inside.discard()
                free.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "long_jump", timeoutTicks = 200)
    fun goatJumpThroughShipWallIsNotClear(helper: GameTestHelper) {
        val wall = box(BlockPos(0, 0, -2), BlockPos(0, 3, 2), stone)
        helper.withShip(wall, helper.absolutePos(BlockPos(10, 5, 8))) { ship ->
            val base = ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -0.5, 0.0)
            val goat = helper.level.spawnAt(EntityType.GOAT, base.add(-3.0, 0.0, 0.0)) { it.setNoAi(true) }
            try {
                val jump = LongJumpToRandomPos<Mob>(UniformInt.of(1, 2), 4, 4, 1.0f, Function { SoundEvents.GOAT_LONG_JUMP })
                val clear = method(LongJumpToRandomPos::class.java, "isClearTransition", Mob::class.java, EntityDimensions::class.java, Vec3::class.java, Vec3::class.java)
                val dims = goat.getDimensions(goat.pose)
                helper.check(clear.invoke(jump, goat, dims, base.add(-3.0, 0.0, 0.0), base.add(-3.0, 0.0, 3.0)) as Boolean) {
                    "goat jump through open air wasn't clear; test setup is wrong"
                }
                helper.check(!(clear.invoke(jump, goat, dims, base.add(-3.0, 0.0, 0.0), base.add(3.0, 0.0, 0.0)) as Boolean)) {
                    "goat jump straight through a ship wall was considered clear"
                }
            } finally {
                goat.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "warden_spawn", timeoutTicks = 200)
    fun wardenCannotSpawnInsideShip(helper: GameTestHelper) {
        val solid = box(BlockPos(-1, 0, -1), BlockPos(1, 2, 1), stone)
        helper.withShip(solid, helper.absolutePos(BlockPos(10, 4, 10))) { ship ->
            val inside = helper.level.spawnAt(EntityType.WARDEN, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -0.5, 0.0)) { it.setNoAi(true) }
            val free = helper.level.spawnAt(EntityType.WARDEN, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(3, 2, 3)))) { it.setNoAi(true) }
            try {
                helper.check(free.checkSpawnObstruction(helper.level)) {
                    "warden in open space was obstructed; test setup is wrong (noColl=${helper.level.noCollision(free)}, " +
                        "unobstructed=${helper.level.isUnobstructed(free)}, liquid=${helper.level.containsAnyLiquid(free.boundingBox)})"
                }
                helper.check(!inside.checkSpawnObstruction(helper.level)) { "warden inside ship blocks was allowed to spawn" }
            } finally {
                inside.discard()
                free.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "follow_owner", timeoutTicks = 200)
    fun petCannotTeleportIntoShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(10, 2, 8))) { ship ->
            val wolf = helper.level.spawnAt(EntityType.WOLF, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(4, 2, 4)))) { it.setNoAi(true) }
            try {
                val goal = FollowOwnerGoal(wolf, 1.0, 10f, 2f, false)
                val canTeleport = method(FollowOwnerGoal::class.java, "canTeleportTo", BlockPos::class.java)
                helper.check(canTeleport.invoke(goal, helper.absolutePos(BlockPos(4, 2, 8))) as Boolean) {
                    "wolf couldn't teleport onto open floor; test setup is wrong (path=" +
                        "${net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getBlockPathTypeStatic(helper.level, helper.absolutePos(BlockPos(4, 2, 8)).mutable())})"
                }
                val occupied = BlockPos.containing(ship.worldCenter(ship.shipPos(0, 0, 0)))
                helper.check(!(canTeleport.invoke(goal, occupied) as Boolean)) { "wolf was allowed to teleport into a ship block" }
            } finally {
                wolf.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "evoker_fangs", timeoutTicks = 200)
    fun evokerFangsLandOnShipDeck(helper: GameTestHelper) {
        val deck = box(BlockPos(-1, 0, -1), BlockPos(1, 0, 1), stone)
        helper.withShip(deck, helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val c = ship.worldCenter(ship.shipPos(0, 0, 0))
            val top = Math.round(c.y + 0.5).toDouble()
            val evoker = helper.level.spawnAt(EntityType.EVOKER, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) { it.setNoAi(true) }
            try {
                val goal = innerGoal("monster.Evoker\$EvokerAttackSpellGoal", evoker)
                method(goal.javaClass, "createSpellEntity",
                    Double::class.javaPrimitiveType!!, Double::class.javaPrimitiveType!!, Double::class.javaPrimitiveType!!,
                    Double::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
                ).invoke(goal, c.x, c.z, top - 1.0, top + 1.0, 0f, 0)
                val fangs = helper.level.getEntitiesOfClass(EvokerFangs::class.java, AABB(c.x - 1, top - 0.5, c.z - 1, c.x + 1, top + 0.5, c.z + 1))
                helper.check(fangs.isNotEmpty()) { "evoker fangs weren't placed on a ship deck" }
                fangs.forEach { it.discard() }
            } finally {
                evoker.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "silverfish_wake", timeoutTicks = 200)
    fun silverfishWakesInfestedShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to Blocks.INFESTED_STONE.defaultBlockState()), helper.absolutePos(BlockPos(8, 2, 8))) { ship ->
            val infested = ship.shipPos(0, 0, 0)
            val silverfish = helper.level.spawnAt(EntityType.SILVERFISH, ship.worldCenter(infested).add(-2.0, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                val goal = innerGoal("monster.Silverfish\$SilverfishWakeUpFriendsGoal", silverfish)
                field(goal.javaClass, "lookForFriends").setInt(goal, 1)
                method(goal.javaClass, "tick").invoke(goal)
                helper.check(!helper.level.getBlockState(infested).`is`(Blocks.INFESTED_STONE)) {
                    "silverfish didn't wake the infested ship block 2 blocks away"
                }
            } finally {
                silverfish.discard()
                helper.level.getEntitiesOfClass(Silverfish::class.java, AABB(infested).inflate(4.0)).forEach { it.discard() }
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "dragon_walls", timeoutTicks = 200)
    fun dragonBreaksShipBlockInItsPath(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val block = ship.shipPos(0, 0, 0)
            val dragon = EntityType.ENDER_DRAGON.create(helper.level)!!
            val cell = AABB(BlockPos.containing(ship.worldCenter(block))).deflate(0.1)
            method(EnderDragon::class.java, "checkWalls", AABB::class.java).invoke(dragon, cell)
            helper.check(helper.level.getBlockState(block).isAir) { "dragon flying through a ship block didn't break it" }
        }
    }

    @GameTest(template = TEST_AREA, batch = "wither_destroy", timeoutTicks = 200)
    fun witherBreaksAdjacentShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(9, 3, 8))) { ship ->
            val block = ship.shipPos(0, 0, 0)
            val wither = helper.level.spawnAt(EntityType.WITHER, ship.worldCenter(block).add(-1.0, -1.5, 0.0)) { it.setNoAi(true) }
            try {
                field(WitherBoss::class.java, "destroyBlocksTick").setInt(wither, 1)
                method(WitherBoss::class.java, "customServerAiStep").invoke(wither)
                helper.check(helper.level.getBlockState(block).isAir) { "wither next to a ship block didn't break it" }
            } finally {
                wither.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "turtle_nav", timeoutTicks = 200)
    fun turtleCanStopAboveShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val turtle = helper.level.spawnAt(EntityType.TURTLE, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) { it.setNoAi(true) }
            try {
                val above = BlockPos.containing(ship.worldCenter(ship.shipPos(0, 0, 0))).above()
                helper.check(!turtle.navigation.isStableDestination(above.above(2))) { "turtle could stop in mid-air; test setup is wrong" }
                helper.check(turtle.navigation.isStableDestination(above)) { "turtle couldn't stop on top of a ship block" }
            } finally {
                turtle.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "strider_nav", timeoutTicks = 200)
    fun striderCanStopInShipLava(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to Blocks.LAVA.defaultBlockState()), helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val strider = helper.level.spawnAt(EntityType.STRIDER, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) { it.setNoAi(true) }
            try {
                val lava = BlockPos.containing(ship.worldCenter(ship.shipPos(0, 0, 0)))
                helper.check(!strider.navigation.isStableDestination(lava.above(2))) { "strider could stop in mid-air; test setup is wrong" }
                helper.check(strider.navigation.isStableDestination(lava)) { "strider couldn't stop in ship lava" }
            } finally {
                strider.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "water_nav", timeoutTicks = 200)
    fun swimmerCannotStopInsideShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val squid = helper.level.spawnAt(EntityType.DOLPHIN, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) { it.setNoAi(true) }
            try {
                val occupied = BlockPos.containing(ship.worldCenter(ship.shipPos(0, 0, 0)))
                helper.check(squid.navigation.isStableDestination(occupied.above(2))) { "dolphin couldn't stop in open space; test setup is wrong" }
                helper.check(!squid.navigation.isStableDestination(occupied)) { "dolphin could stop inside a ship block" }
            } finally {
                squid.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "zombie_villager_cure", timeoutTicks = 200)
    fun ironBarsOnShipSpeedUpCuring(helper: GameTestHelper) {
        val bars = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), Blocks.IRON_BARS.defaultBlockState())
        helper.withShip(bars, helper.absolutePos(BlockPos(8, 4, 8))) { ship ->
            val zv = helper.level.spawnAt(EntityType.ZOMBIE_VILLAGER, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -2.5, 0.0)) { it.setNoAi(true) }
            try {
                val progress = method(ZombieVillager::class.java, "getConversionProgress")
                val sped = (0 until 4000).any { (progress.invoke(zv) as Int) > 1 }
                helper.check(sped) { "iron bars on a ship next to a curing zombie villager never sped it up" }
            } finally {
                zv.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "enderman_take", timeoutTicks = 200)
    fun endermanPicksUpShipBlock(helper: GameTestHelper) {
        val dirt = box(BlockPos(-2, 0, -2), BlockPos(2, 2, 2), Blocks.DIRT.defaultBlockState())
        helper.withShip(dirt, helper.absolutePos(BlockPos(8, 4, 8))) { ship ->
            val enderman = helper.level.spawnAt(EntityType.ENDERMAN, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                val goal = innerGoal("monster.EnderMan\$EndermanTakeBlockGoal", enderman)
                val tick = method(goal.javaClass, "tick")
                for (i in 0 until 300) {
                    tick.invoke(goal)
                    if (enderman.carriedBlock != null) break
                }
                helper.check(enderman.carriedBlock != null) { "enderman surrounded by ship dirt never picked any up" }
            } finally {
                enderman.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "enderman_leave", timeoutTicks = 200)
    fun endermanPlacesBlockOnShipDeck(helper: GameTestHelper) {
        val deck = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), stone)
        helper.withShip(deck, helper.absolutePos(BlockPos(8, 4, 8))) { ship ->
            val top = ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, 0.5, 0.0)
            val enderman = helper.level.spawnAt(EntityType.ENDERMAN, top) { it.setNoAi(true) }
            try {
                enderman.setCarriedBlock(Blocks.DIRT.defaultBlockState())
                val goal = innerGoal("monster.EnderMan\$EndermanLeaveBlockGoal", enderman)
                val tick = method(goal.javaClass, "tick")
                for (i in 0 until 300) {
                    tick.invoke(goal)
                    if (enderman.carriedBlock == null) break
                }
                helper.check(enderman.carriedBlock == null) { "enderman standing on a ship deck never placed its block on it" }
            } finally {
                enderman.discard()
            }
        }
    }

    /** Paints the biome over the block columns between [a] and [b]; returns a function that restores it. */
    private fun ServerLevel.paintBiome(a: BlockPos, b: BlockPos, biome: Holder<Biome>): () -> Unit {
        val qx = QuartPos.fromBlock(minOf(a.x, b.x))..QuartPos.fromBlock(maxOf(a.x, b.x))
        val qz = QuartPos.fromBlock(minOf(a.z, b.z))..QuartPos.fromBlock(maxOf(a.z, b.z))
        val chunks = (SectionPos.blockToSectionCoord(minOf(a.x, b.x))..SectionPos.blockToSectionCoord(maxOf(a.x, b.x))).flatMap { cx ->
            (SectionPos.blockToSectionCoord(minOf(a.z, b.z))..SectionPos.blockToSectionCoord(maxOf(a.z, b.z))).map { cz -> getChunk(cx, cz) }
        }
        val old = HashMap<Long, Holder<Biome>>()
        fun fill(pick: (Long, Holder<Biome>) -> Holder<Biome>) = chunks.forEach { chunk ->
            chunk.fillBiomesFromNoise({ x, y, z, _ ->
                val current = chunk.getNoiseBiome(x, y, z)
                if (x in qx && z in qz) pick(BlockPos.asLong(x, y, z), current) else current
            }, chunkSource.randomState().sampler())
        }
        fill { key, current -> old[key] = current; biome }
        return { fill { key, current -> old[key] ?: current } }
    }

    @GameTest(template = TEST_AREA, batch = "ship_biome", timeoutTicks = 200)
    fun mobSpawnedOnShipUsesWorldBiomeUnderIt(helper: GameTestHelper) {
        val level = helper.level
        val snowy = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS)
        val restore = level.paintBiome(helper.absolutePos(BlockPos(0, 0, 0)), helper.absolutePos(BlockPos(15, 0, 15)), snowy)
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(BlockPos(8, 5, 8))) { ship ->
            try {
                val control = EntityType.FOX.spawn(level, helper.absolutePos(BlockPos(3, 2, 3)), MobSpawnType.SPAWN_EGG)!!
                helper.check(control.variant == Fox.Type.SNOW) { "fox in the painted snowy area isn't a snow fox; test setup is wrong" }
                control.discard()
                val onShip = ship.shipPos(0, 1, 0)
                val shipyardBiome = level.getNoiseBiome(QuartPos.fromBlock(onShip.x), QuartPos.fromBlock(onShip.y), QuartPos.fromBlock(onShip.z))
                helper.check(Fox.Type.byBiome(shipyardBiome) != Fox.Type.SNOW) { "the shipyard itself is snowy; test can't tell the biomes apart" }
                val fox = EntityType.FOX.spawn(level, onShip, MobSpawnType.SPAWN_EGG)!!
                helper.check(fox.variant == Fox.Type.SNOW) { "fox spawned on a ship over snowy plains used the shipyard biome" }
                fox.discard()
            } finally {
                restore()
            }
        }
    }
}
