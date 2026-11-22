package org.valkyrienskies.mod.gametest

import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// World-position block reads that have to find the ship block occupying that cell.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ShipBlockLookupGameTests {

    private val stone = Blocks.STONE.defaultBlockState()

    @GameTest(template = TEST_AREA, batch = "enderman_teleport", timeoutTicks = 200)
    fun endermanTeleportLandsOnShipDeck(helper: GameTestHelper) {
        val deck = box(BlockPos(-2, 0, -2), BlockPos(2, 0, 2), stone)
        helper.withShip(deck, helper.absolutePos(BlockPos(8, 6, 8))) { ship ->
            val deckCenter = ship.worldCenter(ship.shipPos(0, 0, 0))
            val deckTop = deckCenter.y + 0.5
            val enderman = helper.level.spawnAt(EntityType.ENDERMAN, Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2)))) {
                it.setNoAi(true)
            }
            try {
                val teleported = method(
                    EnderMan::class.java, "teleport",
                    Double::class.javaPrimitiveType!!, Double::class.javaPrimitiveType!!, Double::class.javaPrimitiveType!!
                ).invoke(enderman, deckCenter.x, Math.round(deckTop) + 4.0, deckCenter.z) as Boolean
                helper.check(teleported) { "enderman teleport above a ship deck failed" }
                helper.check(abs(enderman.y - deckTop) < 0.6) {
                    "enderman landed at y=${enderman.y}, expected the ship deck at y=$deckTop"
                }
            } finally {
                enderman.discard()
            }
        }
    }
}
