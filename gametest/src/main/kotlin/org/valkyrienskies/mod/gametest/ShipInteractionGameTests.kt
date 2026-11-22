package org.valkyrienskies.mod.gametest

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Player interaction range checks against things on ships.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipInteractionGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    @GameTest(template = TEST_AREA, batch = "chest_minecart", timeoutTicks = 200)
    fun chestMinecartOnShipStaysValidForNearbyPlayer(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(shipAt)) { ship ->
            val above = ship.shipPos(0, 1, 0)
            val cart = helper.level.spawnAt(EntityType.CHEST_MINECART, Vec3.atBottomCenterOf(above))
            val player = helper.mockPlayerAt(ship.worldCenter(above).add(2.0, -0.5, 0.0))
            try {
                helper.check(cart.isChestVehicleStillValid(player)) {
                    "chest minecart on a ship became invalid for a player 2 blocks away"
                }
            } finally {
                cart.discard()
                player.removeMock()
            }
        }
    }
}
