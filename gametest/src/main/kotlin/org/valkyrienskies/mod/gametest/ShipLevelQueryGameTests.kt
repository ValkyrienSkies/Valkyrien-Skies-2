package org.valkyrienskies.mod.gametest

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Ship blocks acting as roof (canSeeSky) and as obstruction (noCollision).
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipLevelQueryGameTests {

    private val stone = Blocks.STONE.defaultBlockState()

    @GameTest(template = TEST_AREA, batch = "sun_burn", timeoutTicks = 200)
    fun zombieUnderShipRoofNeverSunBurns(helper: GameTestHelper) {
        val level = helper.level
        level.dayTime = 6000
        level.setWeatherParameters(6000, 0, false, false)
        val roof = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), stone)
        helper.withShip(roof, helper.absolutePos(BlockPos(11, 8, 8))) { ship ->
            val under = level.spawnAt(EntityType.ZOMBIE, ship.worldCenter(ship.shipPos(0, 0, 0)).add(0.0, -4.0, 0.0)) {
                it.setNoAi(true)
            }
            val open = level.spawnAt(EntityType.ZOMBIE, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) {
                it.setNoAi(true)
            }
            try {
                val sunBurnTick = method(Mob::class.java, "isSunBurnTick")
                val underBurns = (0 until 2000).count { sunBurnTick.invoke(under) as Boolean }
                val openBurns = (0 until 2000).count { sunBurnTick.invoke(open) as Boolean }
                helper.check(openBurns > 0) {
                    "control zombie in open daylight never burned; test setup is wrong " +
                        "(isDay=${level.isDay}, light=${open.lightLevelDependentMagicValue}, sky=${level.canSeeSky(open.blockPosition())}, " +
                        "above=${level.blocksIn(net.minecraft.world.phys.AABB(open.blockPosition()).expandTowards(0.0, 40.0, 0.0))})"
                }
                helper.check(underBurns == 0) { "zombie under a ship roof burned $underBurns times" }
            } finally {
                under.discard()
                open.discard()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "ghast_move", timeoutTicks = 200)
    fun ghastCannotFlyThroughShipWall(helper: GameTestHelper) {
        val wall = box(BlockPos(0, 0, -2), BlockPos(0, 4, 2), stone)
        helper.withShip(wall, helper.absolutePos(BlockPos(13, 6, 8))) { ship ->
            val wallBase = ship.worldCenter(ship.shipPos(0, 0, 0))
            val ghast = helper.level.spawnAt(EntityType.GHAST, wallBase.add(-6.0, -0.5, 0.0)) { it.setNoAi(true) }
            try {
                val control = ghast.moveControl
                val canReach = method(control.javaClass, "canReach", Vec3::class.java, Int::class.javaPrimitiveType!!)
                helper.check(canReach.invoke(control, Vec3(0.0, 0.0, 1.0), 3) as Boolean) {
                    val moved = ghast.boundingBox.move(0.0, 0.0, 1.0)
                    "ghast couldn't move into open space; test setup is wrong (blocks=" +
                        "${helper.level.getBlockCollisions(ghast, moved).count { !it.isEmpty }}, entities=" +
                        "${helper.level.getEntityCollisions(ghast, moved).size}, found=${helper.level.blocksIn(moved)}, box=$moved)"
                }
                helper.check(!(canReach.invoke(control, Vec3(1.0, 0.0, 0.0), 9) as Boolean)) {
                    "ghast path straight through a ship wall was considered clear"
                }
            } finally {
                ghast.discard()
            }
        }
    }
}
