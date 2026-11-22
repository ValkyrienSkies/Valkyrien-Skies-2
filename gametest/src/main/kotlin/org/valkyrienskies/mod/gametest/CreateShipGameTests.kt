package org.valkyrienskies.mod.gametest

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity
import com.simibubi.create.Create
import com.simibubi.create.content.redstone.link.IRedstoneLinkable
import net.createmod.catnip.data.Couple
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler
import com.simibubi.create.content.equipment.toolbox.ToolboxHandler
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock
import com.simibubi.create.content.redstone.thresholdSwitch.ConfigureThresholdSwitchPacket
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Create features that compare positions on a ship with positions in the world.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CreateShipGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(12, 3, 8)

    /** A minimal redstone link at [pos]; Create's range check only reads link locations. */
    private fun linkAt(pos: BlockPos) = object : IRedstoneLinkable {
        override fun getTransmittedStrength() = 15
        override fun setReceivedStrength(power: Int) {}
        override fun isListening() = false
        override fun isAlive() = true
        override fun getNetworkKey(): Couple<RedstoneLinkNetworkHandler.Frequency> =
            Couple.create(RedstoneLinkNetworkHandler.Frequency.EMPTY, RedstoneLinkNetworkHandler.Frequency.EMPTY)
        override fun getLocation() = pos
    }

    @GameTest(template = TEST_AREA, batch = "create_redstone_link", timeoutTicks = 200)
    fun redstoneLinkOnShipIsInRangeOfWorldLink(helper: GameTestHelper) {
        val link = AllBlocks.REDSTONE_LINK.defaultState.setValue(BlockStateProperties.FACING, Direction.UP)
        val blocks = mapOf(BlockPos(0, -1, 0) to stone, BlockPos.ZERO to link.setValue(RedstoneLinkBlock.RECEIVER, true))
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val world = linkAt(BlockPos.containing(ship.worldCenter(ship.shipPos(0, 0, 0)).add(-8.0, 0.0, 0.0)))
            val onShip = linkAt(ship.shipPos(0, 0, 0))
            // Links are compared during network updates; run one so the range check sees a real update.
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(helper.level, world)
            try {
                helper.check(RedstoneLinkNetworkHandler.withinRange(world, onShip)) {
                    "redstone link on a ship 8 blocks from a world link wasn't in range"
                }
            } finally {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(helper.level, world)
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "create_toolbox", timeoutTicks = 200)
    fun toolboxOnShipIsNearestForNearbyPlayer(helper: GameTestHelper) {
        val blocks = mapOf(
            BlockPos(0, -1, 0) to stone,
            BlockPos.ZERO to AllBlocks.TOOLBOXES.get(DyeColor.BROWN).defaultState
        )
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val toolbox = helper.level.getBlockEntity(ship.shipPos(0, 0, 0))!!
            field(toolbox.javaClass, "uniqueId").set(toolbox, java.util.UUID.randomUUID())
            helper.level.tickBlockEntity(ship.shipPos(0, 0, 0), 1)
            val player = helper.mockPlayerAt(ship.worldCenter(ship.shipPos(0, 0, 0)).add(2.0, -0.5, 0.0))
            try {
                val nearest = ToolboxHandler.getNearest(helper.level, player, 8)
                helper.check(nearest.isNotEmpty()) { "toolbox on a ship 2 blocks away wasn't found" }
            } finally {
                player.removeMock()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "create_filter_slot", timeoutTicks = 200)
    fun clickOnShipFilterSlotHitsIt(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos(0, -1, 0) to stone, BlockPos.ZERO to AllBlocks.BRASS_FUNNEL.defaultState)
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val pos = ship.shipPos(0, 0, 0)
            val filtering = BlockEntityBehaviour.get(helper.level, pos, FilteringBehaviour.TYPE)
                ?: throw IllegalStateException("brass funnel has no filtering behaviour")
            val state = helper.level.getBlockState(pos)
            val slot = filtering.slotPositioning
            // Sided slots only accept hits on the face the player clicked; pick a face whose slot is active.
            val local = Direction.values().firstNotNullOfOrNull { face ->
                (slot as? ValueBoxTransform.Sided)?.fromSide(face)
                slot.getLocalOffset(helper.level, pos, state)?.takeIf { slot.testHit(helper.level, pos, state, it) }
            } ?: throw IllegalStateException("brass funnel filter slot accepts no local hit; test setup is wrong")
            val worldHit = ship.toWorld(Vec3.atLowerCornerOf(pos).add(local))
            helper.check(filtering.testHit(worldHit)) { "click at the centre of a ship funnel's filter slot missed it (hit=$worldHit, pos=$pos)" }
        }
    }

    @GameTest(template = TEST_AREA, batch = "create_config_packet", timeoutTicks = 200)
    fun configurationPacketForShipBlockIsApplied(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos(0, -1, 0) to stone, BlockPos.ZERO to AllBlocks.THRESHOLD_SWITCH.defaultState)
        var player: net.minecraft.server.level.ServerPlayer? = null
        // Forge may run the packet's work on the next server tick, so the result is checked a few ticks later.
        helper.withShip(blocks, helper.absolutePos(shipAt), thenAfter = 3, thenCheck = { ship ->
            val pos = ship.shipPos(0, 0, 0)
            try {
                val be = helper.level.getBlockEntity(pos) as ThresholdSwitchBlockEntity
                helper.check(be.offWhenBelow == 3 && be.onWhenAbove == 7) {
                    "threshold switch settings sent by a player 2 blocks away weren't applied (got ${be.offWhenBelow}/${be.onWhenAbove})"
                }
            } finally {
                player?.removeMock()
            }
        }) { ship ->
            val pos = ship.shipPos(0, 0, 0)
            val p = helper.mockPlayerAt(ship.worldCenter(pos).add(2.0, -0.5, 0.0))
            player = p
            handleCreatePacket(ConfigureThresholdSwitchPacket(pos, 3, 7, false, false), p)
        }
    }

    @GameTest(template = TEST_AREA, batch = "chain_conveyor_stats", timeoutTicks = 200)
    fun chainConveyorConnectionOnShipKeepsItsShape(helper: GameTestHelper) {
        val conveyor = AllBlocks.CHAIN_CONVEYOR.defaultState
        // A stone floor keeps the ship in one piece (disconnected parts get split off).
        val blocks = (0..4).associate { BlockPos(it, -1, 0) to stone } + mapOf(BlockPos.ZERO to conveyor, BlockPos(4, 0, 0) to conveyor)
        helper.withShip(blocks, helper.absolutePos(shipAt)) { ship ->
            val be = helper.level.getBlockEntity(ship.shipPos(0, 0, 0)) as ChainConveyorBlockEntity
            helper.check(be.addConnectionTo(ship.shipPos(4, 0, 0))) { "chain conveyor connection wasn't added; test setup is wrong" }
            val stats = be.connectionStats[BlockPos(4, 0, 0)]
            helper.check(stats != null) { "chain connection on a ship was stored under ${be.connections}, not offset (4, 0, 0)" }
            val d = stats!!.end().subtract(stats.start())
            val raw = kotlin.math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
            helper.check(kotlin.math.abs(stats.chainLength() - raw) < 1e-3) {
                "chain on a ship has length ${stats.chainLength()}, but its ends are $raw apart"
            }
        }
    }
}
