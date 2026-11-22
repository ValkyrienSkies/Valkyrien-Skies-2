package org.valkyrienskies.mod.gametest.client

import com.simibubi.create.AllBlocks
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

    val all: List<ClientShipTest> = listOf(
        // The camera (player eyes at base + 1.62) sits inside a ship water block with air in the world there.
        ClientShipTest(
            "camera inside ship water is underwater",
            mapOf(BlockPos.ZERO to Blocks.WATER.defaultBlockState()), BlockPos(0, 1, 0)
        ) { mc, _, _ ->
            val fog = mc.gameRenderer.mainCamera.fluidInCamera
            clientCheck(fog == FogType.WATER) { "camera inside a ship water block reported $fog" }
        },
        // The player looks south at a ship block with a villager behind it. The block hit's location is in
        // shipyard coordinates, so the entity-vs-block distance comparison has to be ship-aware.
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
