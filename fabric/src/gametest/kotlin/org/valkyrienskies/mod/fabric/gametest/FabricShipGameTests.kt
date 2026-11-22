package org.valkyrienskies.mod.fabric.gametest

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.gametest.*

// Fabric's server-side use-item range check against ship blocks.
class FabricShipGameTests {

    @GameTest(template = TEST_AREA, batch = "fabric_use_item_reach", timeoutTicks = 200)
    fun playerCanPlaceOnAdjacentShipBlock(helper: GameTestHelper) {
        helper.withShip(mapOf(BlockPos.ZERO to Blocks.STONE.defaultBlockState()), helper.absolutePos(BlockPos(8, 5, 8))) { ship ->
            val target = ship.shipPos(0, 0, 0)
            val player = helper.mockPlayerAt(ship.worldCenter(target).add(2.0, -0.5, 0.0))
            try {
                player.setGameMode(GameType.CREATIVE)
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.OAK_PLANKS))
                // The client sends ship hits in shipyard coordinates.
                val hit = BlockHitResult(Vec3.atCenterOf(target).add(0.0, 0.5, 0.0), Direction.UP, target, false)
                player.connection.handleUseItemOn(ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 1))
                helper.check(helper.level.getBlockState(target.above()).`is`(Blocks.OAK_PLANKS)) {
                    "placing a block on a ship block 2 blocks away was rejected"
                }
            } finally {
                player.removeMock()
            }
        }
    }
}
