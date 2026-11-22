package org.valkyrienskies.mod.gametest

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity
import com.simibubi.create.content.logistics.packagePort.PackagePortPlacementPacket
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget
import com.simibubi.create.content.trains.track.BezierConnection
import com.simibubi.create.content.trains.track.TrackBlock
import com.simibubi.create.content.trains.track.CurvedTrackDestroyPacket
import com.simibubi.create.content.trains.track.TrackBlockEntity
import com.simibubi.create.content.trains.track.TrackMaterial
import net.createmod.catnip.data.Couple
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Create track and logistics packets for blocks on a ship, sent by a player standing next to the ship.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CreateLogisticsShipGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    @GameTest(template = TEST_AREA, batch = "create_curved_track_destroy", timeoutTicks = 200)
    fun curvedTrackOnShipCanBeDestroyedByNearbyPlayer(helper: GameTestHelper) {
        val track = AllBlocks.TRACK.defaultState.setValue(TrackBlock.HAS_BE, true)
        // A stone floor keeps the ship in one piece (disconnected parts get split off).
        val blocks = (0..4).associate { BlockPos(it, -1, 0) to stone } + mapOf(BlockPos.ZERO to track, BlockPos(4, 0, 0) to track)
        var player: ServerPlayer? = null
        helper.withShip(blocks, helper.absolutePos(shipAt), thenAfter = 3, thenCheck = { ship ->
            try {
                // Without curves Create drops the track's block entity, so a missing one also means removed.
                val pos = ship.shipPos(0, 0, 0)
                helper.check(AllBlocks.TRACK.has(helper.level.getBlockState(pos))) { "ship track block itself disappeared; test setup is wrong" }
                val be = helper.level.getBlockEntity(pos) as? TrackBlockEntity
                helper.check(be == null || be.connections.isEmpty()) { "curved track on a ship wasn't removed for a player 2 blocks away" }
            } finally {
                player?.removeMock()
            }
        }) { ship ->
            val from = ship.shipPos(0, 0, 0)
            val to = ship.shipPos(4, 0, 0)
            val be = helper.level.getBlockEntity(from) as? TrackBlockEntity
                ?: throw IllegalStateException("no track block entity on the ship; test setup is wrong")
            be.addConnection(BezierConnection(
                Couple.create(from, to),
                Couple.create(Vec3.atBottomCenterOf(from), Vec3.atBottomCenterOf(to)),
                Couple.create(Vec3(1.0, 0.0, 0.0), Vec3(-1.0, 0.0, 0.0)),
                Couple.create(Vec3(0.0, 1.0, 0.0), Vec3(0.0, 1.0, 0.0)),
                true, false, TrackMaterial.ANDESITE
            ))
            helper.check(be.connections.isNotEmpty()) { "couldn't add a curve to the ship track; test setup is wrong" }
            val p = helper.mockPlayerAt(ship.worldCenter(from).add(2.0, -0.5, 1.0))
            player = p
            handleCreatePacket(CurvedTrackDestroyPacket(from, to, from, false), p)
        }
    }

    /** A package port target at a fixed world point; just enough for the placement packet's range check. */
    private class FixedTarget(private val location: Vec3) : PackagePortTarget("create:chain_conveyor", BlockPos.ZERO) {
        override fun export(level: LevelAccessor, portPos: BlockPos, box: ItemStack, simulate: Boolean) = false
        override fun getExactTargetLocation(port: PackagePortBlockEntity, level: LevelAccessor, portPos: BlockPos) = location
        override fun getIcon(): ItemStack = ItemStack.EMPTY
        override fun canSupport(be: BlockEntity) = true
        override fun writeInternal(tag: CompoundTag) {}
        override fun readInternal(tag: CompoundTag) {}
    }

    @GameTest(template = TEST_AREA, batch = "create_package_port_placement", timeoutTicks = 200)
    fun packagePortOnShipAcceptsNearbyWorldTarget(helper: GameTestHelper) {
        val blocks = mapOf(BlockPos(0, -1, 0) to stone, BlockPos.ZERO to AllBlocks.PACKAGE_FROGPORT.defaultState)
        var player: ServerPlayer? = null
        var target: PackagePortTarget? = null
        helper.withShip(blocks, helper.absolutePos(shipAt), thenAfter = 3, thenCheck = { ship ->
            try {
                val port = helper.level.getBlockEntity(ship.shipPos(0, 0, 0)) as PackagePortBlockEntity
                helper.check(port.target === target) { "ship package port rejected a target 3 blocks away" }
            } finally {
                player?.removeMock()
            }
        }) { ship ->
            val pos = ship.shipPos(0, 0, 0)
            val p = helper.mockPlayerAt(ship.worldCenter(pos).add(2.0, -0.5, 0.0))
            player = p
            val t = FixedTarget(ship.worldCenter(pos).add(3.0, -0.5, 0.0))
            target = t
            // The handler opens the port's menu for the sender afterwards, which a fake player may not support.
            runCatching { handleCreatePacket(PackagePortPlacementPacket(t, pos), p) }
        }
    }
}
