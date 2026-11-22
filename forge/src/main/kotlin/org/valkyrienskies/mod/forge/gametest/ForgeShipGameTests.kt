package org.valkyrienskies.mod.forge.gametest

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.gametest.*

// Forge's own player reach check against ship blocks.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ForgeShipGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    @GameTest(template = TEST_AREA, batch = "forge_reach", timeoutTicks = 200)
    fun playerCanReachAdjacentShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(shipAt)) { ship ->
            val block = ship.shipPos(0, 0, 0)
            val player = helper.mockPlayerAt(ship.worldCenter(block).add(2.0, -0.5, 0.0))
            try {
                helper.check(player.canReach(block, 0.0)) { "player 2 blocks from a ship block couldn't reach it" }
            } finally {
                player.removeMock()
            }
        }
    }
}
