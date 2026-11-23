package org.valkyrienskies.mod.gametest

import java.util.concurrent.atomic.AtomicInteger
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.mod.api.positionToShip
import org.valkyrienskies.mod.api.positionToWorld
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.DoubleTransformWarnings

// Transforming a point that's already in the target space: reported by vs-core's matrices, skipped by VS's helpers.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipTransformGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    private fun near(a: Vec3, b: Vec3) = Math.abs(a.x - b.x) < 1e-6 && Math.abs(a.y - b.y) < 1e-6 && Math.abs(a.z - b.z) < 1e-6

    @GameTest(template = TEST_AREA, batch = "double_transform", timeoutTicks = 200)
    fun doubleShipTransformIsReported(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(shipAt)) { ship ->
            val count = Class.forName("org.valkyrienskies.core.impl.bodies.properties.DoubleTransformWarnings")
                .getField("count").get(null) as AtomicInteger
            val shipyard = Vec3.atCenterOf(ship.shipPos(0, 0, 0))
            val world = ship.toWorld(shipyard)
            val transform = ship.ship.transform
            fun reported(body: () -> Unit): Int {
                val before = count.get()
                body()
                return count.get() - before
            }
            helper.check(reported { transform.shipToWorld.transformPosition(Vector3d(shipyard.x, shipyard.y, shipyard.z)) } == 0) {
                "ship-to-world of a shipyard point was reported"
            }
            helper.check(reported { transform.worldToShip.transformPosition(Vector3d(world.x, world.y, world.z)) } == 0) {
                "world-to-ship of a world point was reported"
            }
            helper.check(reported { transform.shipToWorld.transformPosition(Vector3d(world.x, world.y, world.z)) } >= 1) {
                "ship-to-world of a world point wasn't reported"
            }
            helper.check(reported { transform.worldToShip.transformPosition(shipyard.x, shipyard.y, shipyard.z, Vector3d()) } >= 1) {
                "world-to-ship of a shipyard point wasn't reported"
            }

            // VS's helpers leave a point already in the target space alone, and warn.
            val skipped = DoubleTransformWarnings.count
            fun skippedBy(body: () -> Unit): Int {
                val before = skipped.get()
                body()
                return skipped.get() - before
            }
            helper.check(skippedBy { helper.check(ship.ship.positionToWorld(world) == world) { "positionToWorld moved a world point" } } == 1) {
                "positionToWorld of a world point didn't warn"
            }
            helper.check(skippedBy { helper.check(ship.ship.positionToShip(shipyard) == shipyard) { "positionToShip moved a shipyard point" } } == 1) {
                "positionToShip of a shipyard point didn't warn"
            }
            helper.check(skippedBy { helper.check(ship.ship.toWorldCoordinates(world) == world) { "toWorldCoordinates moved a world point" } } == 1) {
                "toWorldCoordinates of a world point didn't warn"
            }
            helper.check(skippedBy { helper.check(near(ship.ship.positionToWorld(shipyard), world)) { "positionToWorld of a shipyard point was wrong" } } == 0) {
                "positionToWorld of a shipyard point warned"
            }
        }
    }
}
