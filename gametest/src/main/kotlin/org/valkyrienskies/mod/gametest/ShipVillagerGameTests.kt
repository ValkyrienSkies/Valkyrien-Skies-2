package org.valkyrienskies.mod.gametest

import java.util.Optional
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite
import net.minecraft.world.entity.ai.behavior.HarvestFarmland
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi
import net.minecraft.world.entity.ai.behavior.StrollToPoi
import net.minecraft.world.entity.ai.behavior.StrollToPoiList
import net.minecraft.world.entity.ai.behavior.UseBonemeal
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi
import net.minecraft.world.entity.ai.behavior.WorkAtPoi
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.raid.Raid
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Villager brain behaviours that compare a remembered (ship) position with the villager's world position.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipVillagerGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    /** Ship with a single block at (0,0,0), a villager [offset] from its world centre, then [body]. */
    private fun GameTestHelper.villagerByShipBlock(
        block: net.minecraft.world.level.block.state.BlockState = stone,
        offsetX: Double = 1.0,
        extra: Map<BlockPos, net.minecraft.world.level.block.state.BlockState> = emptyMap(),
        body: (TestShip, BlockPos, Villager) -> Unit
    ) {
        withShip(mapOf(BlockPos.ZERO to block) + extra, absolutePos(shipAt)) { ship ->
            val pos = ship.shipPos(0, 0, 0)
            val villager = level.spawnAt(EntityType.VILLAGER, ship.worldCenter(pos).add(offsetX, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                body(ship, pos, villager)
            } finally {
                villager.discard()
            }
        }
    }

    private fun GameTestHelper.global(pos: BlockPos) = GlobalPos.of(level.dimension(), pos)

    @GameTest(template = TEST_AREA, batch = "stroll_to_poi", timeoutTicks = 200)
    fun strollToShipPoiStartsWhenNearby(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        villager.brain.setMemory(MemoryModuleType.JOB_SITE, helper.global(pos))
        val started = StrollToPoi.create(MemoryModuleType.JOB_SITE, 0.4f, 1, 10).tryStart(helper.level, villager, helper.level.gameTime + 1000)
        helper.check(started && villager.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            "stroll to a ship job site 1 block away didn't start"
        }
    }

    @GameTest(template = TEST_AREA, batch = "stroll_around_poi", timeoutTicks = 200)
    fun strollAroundShipPoiStartsWhenNearby(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        villager.brain.setMemory(MemoryModuleType.JOB_SITE, helper.global(pos))
        val started = StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4f, 5).tryStart(helper.level, villager, helper.level.gameTime + 1000)
        helper.check(started) { "stroll around a ship job site 1 block away didn't start" }
    }

    @GameTest(template = TEST_AREA, batch = "stroll_to_poi_list", timeoutTicks = 200)
    fun strollToShipPoiListStartsWhenNearby(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        villager.brain.setMemory(MemoryModuleType.JOB_SITE, helper.global(pos))
        villager.brain.setMemory(MemoryModuleType.SECONDARY_JOB_SITE, listOf(helper.global(pos)))
        val started = StrollToPoiList.create(MemoryModuleType.SECONDARY_JOB_SITE, 0.4f, 1, 6, MemoryModuleType.JOB_SITE)
            .tryStart(helper.level, villager, helper.level.gameTime + 1000)
        helper.check(started) { "stroll to a ship job-site list 1 block away didn't start" }
    }

    @GameTest(template = TEST_AREA, batch = "validate_nearby_poi", timeoutTicks = 200)
    fun nearbyShipPoiIsValidated(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        villager.brain.setMemory(MemoryModuleType.JOB_SITE, helper.global(pos))
        // No POI is registered at the ship block, so validating it (only done when close) erases the memory.
        val ran = ValidateNearbyPoi.create({ true }, MemoryModuleType.JOB_SITE).tryStart(helper.level, villager, helper.level.gameTime)
        helper.check(ran && !villager.brain.hasMemoryValue(MemoryModuleType.JOB_SITE)) {
            "a ship job site 1 block away wasn't validated"
        }
    }

    @GameTest(template = TEST_AREA, batch = "assign_profession", timeoutTicks = 200)
    fun potentialShipJobSiteIsClaimedWhenAdjacent(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        villager.brain.setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, helper.global(pos))
        AssignProfessionFromJobSite.create().tryStart(helper.level, villager, helper.level.gameTime)
        helper.check(villager.brain.getMemory(MemoryModuleType.JOB_SITE) == Optional.of(helper.global(pos))) {
            "an adjacent potential ship job site wasn't claimed"
        }
    }

    @GameTest(template = TEST_AREA, batch = "work_at_poi", timeoutTicks = 200)
    fun workAtAdjacentShipJobSite(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        villager.brain.setMemory(MemoryModuleType.JOB_SITE, helper.global(pos))
        val work = WorkAtPoi()
        val canStillUse = method(WorkAtPoi::class.java, "canStillUse", ServerLevel::class.java, Villager::class.java, Long::class.javaPrimitiveType!!)
        helper.check(canStillUse.invoke(work, helper.level, villager, helper.level.gameTime) as Boolean) {
            "work at an adjacent ship job site stopped"
        }
        // checkExtraStartConditions also rolls a coin; retry with its cooldown reset.
        val check = method(WorkAtPoi::class.java, "checkExtraStartConditions", ServerLevel::class.java, Villager::class.java)
        val lastCheck = field(WorkAtPoi::class.java, "lastCheck")
        val started = (0 until 64).any {
            lastCheck.setLong(work, Long.MIN_VALUE / 2)
            check.invoke(work, helper.level, villager) as Boolean
        }
        helper.check(started) { "work at an adjacent ship job site never started" }
    }

    @GameTest(template = TEST_AREA, batch = "walk_target_sink", timeoutTicks = 200)
    fun walkTargetOnShipCountsAsReached(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        val reached = method(MoveToTargetSink::class.java, "reachedTarget", Mob::class.java, WalkTarget::class.java)
            .invoke(MoveToTargetSink(), villager, WalkTarget(pos, 0.5f, 2)) as Boolean
        helper.check(reached) { "walk target on a ship 1 block away wasn't reached" }
    }

    @GameTest(template = TEST_AREA, batch = "walk_target_from_memory", timeoutTicks = 200)
    fun nearbyShipHomeNeedsNoWalkTarget(helper: GameTestHelper) = helper.villagerByShipBlock { _, pos, villager ->
        villager.brain.setMemory(MemoryModuleType.HOME, helper.global(pos))
        SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, 0.4f, 3, 10, 1200).tryStart(helper.level, villager, helper.level.gameTime)
        helper.check(villager.brain.hasMemoryValue(MemoryModuleType.HOME) && !villager.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            "villager 1 block from its ship home tried to walk there or forgot it"
        }
    }

    @GameTest(template = TEST_AREA, batch = "harvest_farmland", timeoutTicks = 200)
    fun farmerHarvestsAdjacentShipCrop(helper: GameTestHelper) {
        val wheat = (Blocks.WHEAT as CropBlock).getStateForAge(7)
        helper.withShip(mapOf(BlockPos(0, -1, 0) to Blocks.FARMLAND.defaultBlockState(), BlockPos.ZERO to wheat), helper.absolutePos(shipAt)) { ship ->
            val crop = ship.shipPos(0, 0, 0)
            val villager = helper.level.spawnAt(EntityType.VILLAGER, ship.worldCenter(crop).add(0.4, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                val harvest = HarvestFarmland()
                field(HarvestFarmland::class.java, "aboveFarmlandPos").set(harvest, crop)
                field(HarvestFarmland::class.java, "nextOkStartTime").setLong(harvest, 0L)
                method(HarvestFarmland::class.java, "tick", ServerLevel::class.java, Villager::class.java, Long::class.javaPrimitiveType!!)
                    .invoke(harvest, helper.level, villager, helper.level.gameTime + 10)
                helper.check(helper.level.getBlockState(crop) != wheat) { "farmer standing on a ship crop didn't harvest it" }
            } finally {
                villager.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "use_bonemeal", timeoutTicks = 200)
    fun villagerBonemealsAdjacentShipCrop(helper: GameTestHelper) {
        val young = (Blocks.WHEAT as CropBlock).getStateForAge(0)
        helper.withShip(mapOf(BlockPos(0, -1, 0) to Blocks.FARMLAND.defaultBlockState(), BlockPos.ZERO to young), helper.absolutePos(shipAt)) { ship ->
            val crop = ship.shipPos(0, 0, 0)
            val villager = helper.level.spawnAt(EntityType.VILLAGER, ship.worldCenter(crop).add(0.4, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                villager.inventory.addItem(ItemStack(Items.BONE_MEAL, 16))
                val bonemeal = UseBonemeal()
                field(UseBonemeal::class.java, "cropPos").set(bonemeal, Optional.of(crop))
                field(UseBonemeal::class.java, "nextWorkCycleTime").setLong(bonemeal, 0L)
                method(UseBonemeal::class.java, "tick", ServerLevel::class.java, Villager::class.java, Long::class.javaPrimitiveType!!)
                    .invoke(bonemeal, helper.level, villager, helper.level.gameTime + 10)
                helper.check((Blocks.WHEAT as CropBlock).getAge(helper.level.getBlockState(crop)) > 0) {
                    "villager next to a ship crop didn't bonemeal it"
                }
            } finally {
                villager.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "raid_raiders", timeoutTicks = 200)
    fun raiderNearShipRaidCentreStaysInRaid(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(shipAt)) { ship ->
            val centre = ship.shipPos(0, 0, 0)
            val pillager = helper.level.spawnAt(EntityType.PILLAGER, ship.worldCenter(centre).add(4.0, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                val raid = Raid(987654, helper.level, centre)
                raid.joinRaid(1, pillager, null, false)
                method(Raid::class.java, "updateRaiders").invoke(raid)
                helper.check(raid.totalRaidersAlive == 1) { "pillager 4 blocks from a ship raid centre was dropped from the raid" }
            } finally {
                pillager.discard()
            }
        }
    }
}
