package org.valkyrienskies.mod.gametest

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.sqrt
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket
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
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.mod.mixinducks.mod_compat.create.ChainConveyorCrossFrameDuck
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore

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
        var player: ServerPlayer? = null
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
            val raw = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
            helper.check(abs(stats.chainLength() - raw) < 1e-3) {
                "chain on a ship has length ${stats.chainLength()}, but its ends are $raw apart"
            }
        }
    }

    // A chain between a world conveyor and a ship conveyor ends at the other conveyor's current position, both ways.
    @GameTest(template = TEST_AREA, batch = "chain_conveyor_cross_frame", timeoutTicks = 200)
    fun chainConveyorConnectsWorldToShip(helper: GameTestHelper) {
        val conveyor = AllBlocks.CHAIN_CONVEYOR.defaultState
        val worldPos = helper.absolutePos(BlockPos(2, 2, 8))
        helper.level.setBlock(worldPos, conveyor, 3)
        val blocks = mapOf(BlockPos(0, -1, 0) to stone, BlockPos.ZERO to conveyor)
        var player: ServerPlayer? = null
        helper.withShip(blocks, helper.absolutePos(BlockPos(8, 2, 8)), thenAfter = 3, thenCheck = { ship ->
            try {
                val shipPos = ship.shipPos(0, 0, 0)
                val worldBe = helper.level.getBlockEntity(worldPos) as ChainConveyorBlockEntity
                val shipBe = helper.level.getBlockEntity(shipPos) as ChainConveyorBlockEntity
                val offset = shipPos.subtract(worldPos)
                helper.check(offset in worldBe.connections && worldPos.subtract(shipPos) in shipBe.connections) {
                    "chain between a world and a ship conveyor wasn't connected (${worldBe.connections} / ${shipBe.connections})"
                }
                fun checkFollows(what: String) {
                    worldBe.tick() // test areas don't tick block entities
                    shipBe.tick()
                    val stats = worldBe.connectionStats[offset]!!
                    val shipConveyor = ship.toWorld(Vec3.atBottomCenterOf(shipPos))
                    helper.check(stats.end().distanceTo(shipConveyor) < 2.0 && stats.chainLength() < 12f) {
                        "$what: world chain ends at ${stats.end()} (length ${stats.chainLength()}), not at the ship conveyor $shipConveyor"
                    }
                    val back = shipBe.connectionStats[worldPos.subtract(shipPos)]!!
                    val worldConveyor = Vec3.atBottomCenterOf(worldPos)
                    helper.check(ship.toWorld(back.end()).distanceTo(worldConveyor) < 2.0) {
                        "$what: ship chain ends at ${ship.toWorld(back.end())}, not at the world conveyor $worldConveyor"
                    }
                }
                checkFollows("connected")
                // Rotation crosses the chain: a motor under the world conveyor turns the ship conveyor.
                val motorPos = worldPos.below()
                helper.level.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.UP), 3)
                val motor = helper.level.getBlockEntity(motorPos) as KineticBlockEntity
                repeat(3) { motor.tick(); worldBe.tick(); shipBe.tick() }
                helper.check(worldBe.speed != 0f && shipBe.speed != 0f) {
                    "a motor turned the world conveyor at ${worldBe.speed} and the ship conveyor at ${shipBe.speed}"
                }
                // The chain follows the ship as it moves and turns.
                vsCore.teleportShip(
                    helper.level.shipObjectWorld as ServerShipWorld, ship.ship,
                    vsCore.newShipTeleportData(
                        newPos = Vector3d(ship.ship.transform.positionInWorld).add(0.0, 0.0, 1.0),
                        newRot = Quaterniond().rotateY(Math.PI / 4),
                        newDimension = helper.level.dimensionId
                    )
                )
                checkFollows("after moving the ship")
                // Moved past what the paid chains cover, the chain snaps and gives back only what was paid.
                val paid = (worldBe as ChainConveyorCrossFrameDuck).`vs$chainsPaid`(offset)
                helper.check(paid != null && paid >= 1) { "no chains recorded for the cross-frame connection (got $paid)" }
                vsCore.teleportShip(
                    helper.level.shipObjectWorld as ServerShipWorld, ship.ship,
                    vsCore.newShipTeleportData(
                        newPos = Vector3d(ship.ship.transform.positionInWorld).add(10.0, 0.0, 0.0),
                        newDimension = helper.level.dimensionId
                    )
                )
                worldBe.tick()
                helper.check(offset !in worldBe.connections && worldPos.subtract(shipPos) !in shipBe.connections) {
                    "chain stretched past its ${paid} chains didn't snap (${worldBe.connections} / ${shipBe.connections})"
                }
                val dropped = helper.level.getEntitiesOfClass(ItemEntity::class.java, AABB(worldPos).inflate(24.0)) {
                    it.item.`is`(Items.CHAIN)
                }
                val count = dropped.sumOf { it.item.count }
                dropped.forEach { it.discard() }
                helper.check(count == paid) { "snapped chain dropped $count chains, but $paid were paid" }
            } finally {
                player?.removeMock()
            }
        }) { ship ->
            val shipPos = ship.shipPos(0, 0, 0)
            (helper.level.getBlockEntity(worldPos) as ChainConveyorBlockEntity).tick()
            (helper.level.getBlockEntity(shipPos) as ChainConveyorBlockEntity).tick()
            val p = helper.mockPlayerAt(Vec3.atBottomCenterOf(worldPos).add(1.0, 0.0, 0.0))
            p.setGameMode(GameType.CREATIVE)
            player = p
            handleCreatePacket(ChainConveyorConnectionPacket(worldPos, shipPos, ItemStack(Items.CHAIN), true), p)
        }
    }
}
