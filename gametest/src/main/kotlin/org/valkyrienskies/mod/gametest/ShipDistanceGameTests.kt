package org.valkyrienskies.mod.gametest

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.GlobalPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.InteractWithDoor
import net.minecraft.world.entity.ai.behavior.SleepInBed
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.animal.Bee
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.entity.BellBlockEntity
import net.minecraft.world.level.block.entity.ConduitBlockEntity
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.Level
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Distance checks between a ship block and an entity in the world. Each test drives the exact vanilla method
// whose distance comparison used to need its own ship-aware wrapper.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipDistanceGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    @GameTest(template = TEST_AREA, batch = "bell", timeoutTicks = 200)
    fun bellOnShipAlertsNearbyWorldVillager(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos(0, -1, 0) to stone, BlockPos.ZERO to Blocks.BELL.defaultBlockState())
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val bellPos = ship.shipPos(0, 0, 0)
            val villager = helper.level.spawnAt(EntityType.VILLAGER, ship.worldCenter(bellPos).add(3.0, 0.0, 0.0))
            try {
                val bell = helper.level.getBlockEntity(bellPos) as BellBlockEntity
                method(BellBlockEntity::class.java, "updateEntities").invoke(bell)
                helper.check(villager.brain.hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)) {
                    "villager 3 blocks from a ship bell didn't hear it"
                }
            } finally {
                villager.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "door_distance", timeoutTicks = 200)
    fun shipDoorIsNotTooFarForAdjacentWorldVillager(helper: GameTestHelper) {
        val blocks = mapOf(
            BlockPos(0, -1, 0) to stone,
            BlockPos.ZERO to Blocks.OAK_DOOR.defaultBlockState(),
            BlockPos(0, 1, 0) to Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
        )
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val doorPos = ship.shipPos(0, 0, 0)
            val villager = helper.level.spawnAt(EntityType.VILLAGER, ship.worldCenter(doorPos).add(1.0, -0.5, 0.0))
            try {
                val tooFar = method(
                    InteractWithDoor::class.java, "isDoorTooFarAway",
                    ServerLevel::class.java, LivingEntity::class.java, GlobalPos::class.java
                ).invoke(null, helper.level, villager, GlobalPos.of(helper.level.dimension(), doorPos)) as Boolean
                helper.check(!tooFar) { "ship door 1 block away was considered too far" }
            } finally {
                villager.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "sleep_in_bed", timeoutTicks = 200)
    fun villagerNextToShipBedCanStartSleeping(helper: GameTestHelper) {
        val bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
        val blocks = mapOf(
            BlockPos(0, -1, 0) to stone,
            BlockPos(0, -1, -1) to stone,
            BlockPos.ZERO to bed.setValue(BedBlock.PART, BedPart.FOOT),
            BlockPos(0, 0, -1) to bed.setValue(BedBlock.PART, BedPart.HEAD)
        )
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val bedPos = ship.shipPos(0, 0, 0)
            val villager = helper.level.spawnAt(EntityType.VILLAGER, ship.worldCenter(bedPos).add(1.0, -0.5, 0.0))
            try {
                villager.brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.level.dimension(), bedPos))
                val canStart = method(
                    SleepInBed::class.java, "checkExtraStartConditions", ServerLevel::class.java, LivingEntity::class.java
                ).invoke(SleepInBed(), helper.level, villager) as Boolean
                helper.check(canStart) { "villager next to its ship bed couldn't start sleeping" }
            } finally {
                villager.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "allay_jukebox", timeoutTicks = 200)
    fun allayKeepsDancingNextToShipJukebox(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos.ZERO to Blocks.JUKEBOX.defaultBlockState())
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val jukebox = ship.shipPos(0, 0, 0)
            val allay = helper.level.spawnAt(EntityType.ALLAY, ship.worldCenter(jukebox).add(2.0, 0.0, 0.0))
            try {
                field(Allay::class.java, "jukeboxPos").set(allay, jukebox)
                val stop = method(Allay::class.java, "shouldStopDancing").invoke(allay) as Boolean
                helper.check(!stop) { "allay 2 blocks from a ship jukebox stopped dancing" }
            } finally {
                allay.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "parrot_jukebox", timeoutTicks = 200)
    fun parrotKeepsPartyingNextToShipJukebox(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos.ZERO to Blocks.JUKEBOX.defaultBlockState())
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val jukebox = ship.shipPos(0, 0, 0)
            val parrot = helper.level.spawnAt(EntityType.PARROT, ship.worldCenter(jukebox).add(1.5, -0.5, 0.0)) {
                it.setNoAi(true)
            }
            try {
                parrot.setRecordPlayingNearby(jukebox, true)
                parrot.aiStep()
                helper.check(parrot.isPartyParrot) { "parrot next to a ship jukebox stopped partying" }
            } finally {
                parrot.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "bee_closer_than", timeoutTicks = 200)
    fun beeNextToShipBlockIsCloserThanRange(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos.ZERO to Blocks.BEE_NEST.defaultBlockState())
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val nest = ship.shipPos(0, 0, 0)
            val bee = helper.level.spawnAt(EntityType.BEE, ship.worldCenter(nest).add(1.0, 0.0, 0.0)) { it.setNoAi(true) }
            try {
                val close = method(Bee::class.java, "closerThan", BlockPos::class.java, Int::class.javaPrimitiveType!!)
                    .invoke(bee, nest, 3) as Boolean
                helper.check(close) { "bee 1 block from a ship nest wasn't within 3 blocks of it" }
            } finally {
                bee.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "conduit", timeoutTicks = 200)
    fun conduitOnShipEmpowersNearbyWorldPlayer(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos.ZERO to Blocks.CONDUIT.defaultBlockState())
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val conduit = ship.shipPos(0, 0, 0)
            val player = helper.mockPlayerAt(ship.worldCenter(conduit).add(3.0, -0.5, 0.0), inLevel = true)
            try {
                field(Entity::class.java, "wasTouchingWater").setBoolean(player, true)
                val frame = (0 until 16).map { conduit.offset(it, 0, 0) }
                method(ConduitBlockEntity::class.java, "applyEffects", Level::class.java, BlockPos::class.java, List::class.java)
                    .invoke(null, helper.level, conduit, frame)
                helper.check(player.hasEffect(MobEffects.CONDUIT_POWER)) { "player 3 blocks from a ship conduit got no conduit power" }
            } finally {
                player.removeMock()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "particles", timeoutTicks = 200)
    fun particlesAtShipBlockReachNearbyWorldPlayer(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos.ZERO to stone)
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val block = ship.shipPos(0, 0, 0)
            val player = helper.mockPlayerAt(ship.worldCenter(block).add(4.0, -0.5, 0.0))
            try {
                val c = net.minecraft.world.phys.Vec3.atCenterOf(block)
                val sent = helper.level.sendParticles(player, ParticleTypes.FLAME, false, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0)
                helper.check(sent) { "particle at a ship block wasn't sent to a player 4 blocks away" }
            } finally {
                player.removeMock()
            }
        }
    }
}
