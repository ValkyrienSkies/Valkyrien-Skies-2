package org.valkyrienskies.mod.gametest.client

import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.sqrt
import org.joml.Vector3d
import org.valkyrienskies.mod.common.VSClientGameUtils
import org.valkyrienskies.mod.common.util.ShipHitVec3
import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform
import com.simibubi.create.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionRenderer
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer
import net.createmod.catnip.data.Pair
import org.joml.Quaterniond
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore
import net.createmod.catnip.outliner.Outliner
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen
import com.simibubi.create.content.trains.entity.TrainRelocator
import com.simibubi.create.foundation.sound.SoundScapes
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.world.level.material.FogType
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.gametest.field
import org.valkyrienskies.mod.gametest.method
import org.valkyrienskies.mod.gametest.spawnAt

// Client-side code that compares a ship position with the player's or camera's world position.
object ClientShipTests {
    private val stone = Blocks.STONE.defaultBlockState()


    private fun toWorld(shipyard: Vec3): Vec3 {
        val ship = VSClientGameUtils.getClientShip(shipyard.x, shipyard.y, shipyard.z)
            ?: throw ClientTestFailure("no client ship at $shipyard; test setup is wrong")
        return ship.renderTransform.shipToWorld.transformPosition(Vector3d(shipyard.x, shipyard.y, shipyard.z)).let { Vec3(it.x, it.y, it.z) }
    }

    /** Whether the value box Create's renderer outlined under [key] is highlighted (not passive). */
    private fun outlineActive(key: Any): Boolean? = (Outliner.getInstance().outlines[key]?.outline as? ValueBox)?.let { !it.isPassive }

    val all: List<ClientShipTest> = listOf(
        // The client recomputes connection stats from synced data, with its own ship transforms.
        ClientShipTest(
            "chain conveyor connection on a ship keeps its shape on the client",
            (0..4).associate { BlockPos(it, -1, 0) to stone } +
                mapOf(BlockPos.ZERO to AllBlocks.CHAIN_CONVEYOR.defaultState, BlockPos(4, 0, 0) to AllBlocks.CHAIN_CONVEYOR.defaultState),
            BlockPos(0, 2, 4),
            serverSetup = { level, ship, _ ->
                val be = level.getBlockEntity(ship.shipPos(0, 0, 0)) as ChainConveyorBlockEntity
                be.tick() // connection stats are set up on the first tick
                be.addConnectionTo(ship.shipPos(4, 0, 0))
                emptyList()
            }
        ) { mc, ship, _ ->
            val be = mc.level!!.getBlockEntity(ship.shipPos(0, 0, 0)) as? ChainConveyorBlockEntity
            clientCheck(be != null) { "no chain conveyor on the client; test setup is wrong" }
            val stats = be!!.connectionStats[BlockPos(4, 0, 0)]
            clientCheck(stats != null) { "client chain connection stored under ${be.connections}, not offset (4, 0, 0)" }
            val d = stats!!.end().subtract(stats.start())
            val raw = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
            clientCheck(abs(stats.chainLength() - raw) < 1e-3) {
                "client chain on a ship has length ${stats.chainLength()}, but its ends are $raw apart"
            }
        },
        // The crosshair's hit location on a ship block is in world coordinates; Create subtracts the block pos from it.
        ClientShipTest(
            "crafter edge on a ship highlights under the crosshair",
            run {
                val crafter = AllBlocks.MECHANICAL_CRAFTER.defaultState.setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.NORTH)
                mapOf(BlockPos.ZERO to crafter, BlockPos(1, 0, 0) to crafter)
            },
            BlockPos(0, 0, 3)
        ) { mc, ship, _ ->
            val pos = ship.shipPos(0, 0, 0)
            mc.player!!.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack())
            mc.hitResult = BlockHitResult(toWorld(Vec3(pos.x + 0.9, pos.y + 1.0, pos.z + 0.5)), Direction.UP, pos, false)
            EdgeInteractionRenderer.tick()
            val active = outlineActive("edge")
            clientCheck(active == true) { "crosshair on a ship crafter's east edge didn't highlight it (outline active=$active)" }
        },

        // Rotated: on an unrotated ship a wrong ship transform here is undone by the ship-aware distance check in
        // Create's slot hit test, which would hide the bug.
        ClientShipTest(
            "funnel filter slot on a ship highlights under the crosshair",
            mapOf(BlockPos(0, -1, 0) to stone, BlockPos.ZERO to AllBlocks.BRASS_FUNNEL.defaultState),
            BlockPos(0, 1, 3),
            serverSetup = { level, ship, _ ->
                val teleport = vsCore.newShipTeleportData(
                    newPos = ship.ship.transform.positionInWorld,
                    newRot = Quaterniond().rotateY(Math.PI / 4),
                    newDimension = level.dimensionId
                )
                vsCore.teleportShip(level.server.shipObjectWorld as ServerShipWorld, ship.ship, teleport)
                emptyList()
            }
        ) { mc, ship, _ ->
            val level = mc.level!!
            val pos = ship.shipPos(0, 0, 0)
            val filtering = BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE)
            clientCheck(filtering != null) { "ship brass funnel has no filtering behaviour on the client; test setup is wrong" }
            val slot = filtering!!.slotPositioning
            val state = level.getBlockState(pos)
            val (face, local) = Direction.values().firstNotNullOfOrNull { face ->
                (slot as? ValueBoxTransform.Sided)?.fromSide(face)
                slot.getLocalOffset(level, pos, state)?.takeIf { slot.testHit(level, pos, state, it) }?.let { face to it }
            } ?: throw ClientTestFailure("brass funnel filter slot accepts no local hit; test setup is wrong")
            mc.player!!.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            mc.hitResult = BlockHitResult(toWorld(Vec3.atLowerCornerOf(pos).add(local)), face, pos, false)
            FilteringRenderer.tick()
            val active = Outliner.getInstance().outlines.entries
                .firstOrNull { (key, _) -> key is Pair<*, *> && key.second == pos && (key.first as? String)?.startsWith("filter") == true }
                ?.let { (key, _) -> outlineActive(key) }
            clientCheck(active == true) { "crosshair on a ship funnel's filter slot didn't highlight it (outline active=$active)" }
        },

        // The camera (player eyes at base + 1.62) sits inside a ship water block with air in the world there.
        ClientShipTest(
            "camera inside ship water is underwater",
            mapOf(BlockPos.ZERO to Blocks.WATER.defaultBlockState()), BlockPos(0, 1, 0)
        ) { mc, _, _ ->
            val fog = mc.gameRenderer.mainCamera.fluidInCamera
            clientCheck(fog == FogType.WATER) { "camera inside a ship water block reported $fog" }
        },
        // The player looks south at a ship block with a villager behind it. The block hit's location is in world
        // coordinates.
        ClientShipTest(
            "crosshair picks ship block, not the entity behind it",
            mapOf(BlockPos.ZERO to stone), BlockPos(0, 1, 3),
            serverSetup = { level, _, base ->
                listOf(level.spawnAt(EntityType.VILLAGER, Vec3.atBottomCenterOf(base.offset(0, 0, 5))) {
                    it.setNoAi(true)
                    it.isNoGravity = true
                })
            }
        ) { mc, ship, _ ->
            mc.gameRenderer.pick(1f)
            val hit = mc.hitResult
            clientCheck(hit is BlockHitResult && hit.type == HitResult.Type.BLOCK && hit.blockPos == ship.shipPos(0, 0, 0)) {
                "crosshair on a ship block 2.5 blocks away picked $hit (${(hit as? BlockHitResult)?.blockPos})"
            }
            val location = hit!!.location
            clientCheck(location is ShipHitVec3 && location.distanceTo(mc.player!!.eyePosition) < 4.0 && !ShipHitVec3.inShipyard(location.x, location.z)) {
                "crosshair hit on a ship block has location $location (${location.javaClass.simpleName}), not a world-space ship hit"
            }
        },

        ClientShipTest("ship block 3 blocks from the camera is in sound range", mapOf(BlockPos.ZERO to stone), BlockPos(3, 1, 0)) { mc, ship, _ ->
            mc.gameRenderer.mainCamera.setup(mc.level!!, mc.player!!, false, false, 1f)
            val outOfRange = method(SoundScapes::class.java, "outOfRange", BlockPos::class.java)
                .invoke(null, ship.shipPos(0, 0, 0)) as Boolean
            clientCheck(!outOfRange) { "ambient sound source on a ship 3 blocks from the camera was out of range" }
        },

        ClientShipTest(
            "clipboard screen stays open next to a ship clipboard",
            mapOf(BlockPos.ZERO to AllBlocks.CLIPBOARD.defaultState, BlockPos(0, 0, 1) to stone), BlockPos(2, 1, 0)
        ) { mc, ship, _ ->
            var closed = false
            val screen = object : ClipboardScreen(0, AllBlocks.CLIPBOARD.asStack(), ship.shipPos(0, 0, 0)) {
                override fun removed() {
                    closed = true
                }
            }
            screen.init(mc, mc.window.guiScaledWidth, mc.window.guiScaledHeight)
            screen.tick()
            clientCheck(!closed) { "clipboard screen for a ship clipboard 2 blocks away closed itself" }
        },

        ClientShipTest("train relocation from a nearby ship isn't aborted", mapOf(BlockPos.ZERO to stone), BlockPos(3, 1, 0)) { mc, ship, _ ->
            val train = field(TrainRelocator::class.java, "relocatingTrain")
            val origin = field(TrainRelocator::class.java, "relocatingOrigin")
            train.set(null, UUID.randomUUID())
            origin.set(null, Vec3.atCenterOf(ship.shipPos(0, 0, 0)))
            try {
                clickTrainRelocator(mc)
                clientCheck(train.get(null) != null) { "relocating a train from a ship 3 blocks away was aborted as too far" }
            } finally {
                train.set(null, null)
            }
        }
    ) + platformClientTests()
}
