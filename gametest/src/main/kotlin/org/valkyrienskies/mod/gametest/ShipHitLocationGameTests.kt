package org.valkyrienskies.mod.gametest

import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.ShipHitVec3
import org.valkyrienskies.mod.common.vsCore

// Hit locations on ship blocks: world coordinates, with block-relative arithmetic in shipyard coordinates.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipHitLocationGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    private fun near(a: Vec3, b: Vec3) = Math.abs(a.x - b.x) < 1e-6 && Math.abs(a.y - b.y) < 1e-6 && Math.abs(a.z - b.z) < 1e-6

    // Rotated, so a missing or doubled transform can't cancel out.
    private fun rotate(helper: GameTestHelper, ship: TestShip) {
        vsCore.teleportShip(
            helper.level.shipObjectWorld as ServerShipWorld, ship.ship,
            vsCore.newShipTeleportData(
                newPos = ship.ship.transform.positionInWorld,
                newRot = Quaterniond().rotateY(Math.PI / 4),
                newDimension = helper.level.dimensionId
            )
        )
    }

    @GameTest(template = TEST_AREA, batch = "ship_hit_location", timeoutTicks = 200)
    fun shipHitLocationIsWorldButBlockRelativeInShipyard(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(shipAt)) { ship ->
            rotate(helper, ship)
            val pos = ship.shipPos(0, 0, 0)
            val corner = Vec3.atLowerCornerOf(pos)
            val inBlock = Vec3(0.3, 1.0, 0.7)
            val world = ship.toWorld(corner.add(inBlock))
            val hit = BlockHitResult(world, Direction.UP, pos, false)
            val loc = hit.location
            helper.check(loc is ShipHitVec3 && near(loc, world)) { "hit location $loc (${loc.javaClass.simpleName}) isn't the world point $world" }
            helper.check(near(loc.subtract(corner), inBlock)) { "location - block corner = ${loc.subtract(corner)}, not $inBlock" }
            helper.check(near(loc.subtract(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()), inBlock)) {
                "location - block coordinates = ${loc.subtract(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())}, not $inBlock"
            }
            helper.check(near(corner.vectorTo(loc), inBlock)) { "block corner -> location = ${corner.vectorTo(loc)}, not $inBlock" }
            helper.check(near(Vec3.atCenterOf(pos).subtract(loc), Vec3(0.5, 0.5, 0.5).subtract(inBlock))) {
                "block centre - location = ${Vec3.atCenterOf(pos).subtract(loc)}"
            }
            val raised = loc.add(0.0, 0.5, 0.0)
            helper.check(raised is ShipHitVec3 && near(raised.subtract(corner), inBlock.add(0.0, 0.5, 0.0))) {
                "location raised 0.5 in the world, minus the block corner = ${raised.subtract(corner)}"
            }
            helper.check(near(loc.subtract(world), Vec3.ZERO)) { "location - its own world point = ${loc.subtract(world)}" }

            val buf = FriendlyByteBuf(Unpooled.buffer())
            buf.writeBlockHitResult(hit)
            val sent = buf.readBlockHitResult()
            helper.check(sent.blockPos == pos && near(sent.location.subtract(corner), inBlock) && Math.abs(sent.location.x - (corner.x + inBlock.x)) < 1e-3) {
                "hit sent over the network arrived at ${sent.location}, not ${corner.add(inBlock)}"
            }
        }
    }
}
