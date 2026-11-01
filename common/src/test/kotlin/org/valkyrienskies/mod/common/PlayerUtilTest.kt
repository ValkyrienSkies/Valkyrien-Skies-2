package org.valkyrienskies.mod.common

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor

class PlayerUtilTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private val level = mockk<Level>()
    private val ship = mockk<LoadedShip>()
    private val player = mockk<Player>(moreInterfaces = arrayOf(EntityAccessor::class))
    private val worldPosition = Vec3(10.0, 64.0, 20.0)
    private var position = worldPosition
    private var yaw = 0.0f
    private var pitch = 60.0f

    @BeforeEach
    fun setup() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { level.isBlockInShipyard(any<Double>(), any<Double>(), any<Double>()) } answers {
            secondArg<Double>() > 1_000_000.0
        }
        every { ship.worldToShip } returns Matrix4d().translation(2_000_000.0, 0.0, 0.0).rotateY(Math.PI / 2)
        every { player.level() } returns level
        every { player.position() } answers { position }
        every { player.x } answers { position.x }
        every { player.y } answers { position.y }
        every { player.z } answers { position.z }
        every { player.yRot } answers { yaw }
        every { player.xRot } answers { pitch }
        every { player.yRot = any() } answers { yaw = firstArg() }
        every { player.xRot = any() } answers { pitch = firstArg() }
        every { player.getViewYRot(1.0f) } answers { yaw }
        every { player.getViewXRot(1.0f) } answers { pitch }
        every { player.lookAngle } answers { Vec3.directionFromRotation(pitch, yaw) }
        every { (player as EntityAccessor).setPosNoUpdates(any()) } answers { position = firstArg() }
        player.yHeadRot = 15.0f
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `floor mounted blocks retain ship facing after querying nearest directions`() {
        val context = mockk<BlockPlaceContext>()
        val blockPos = BlockPos(2_000_020, 64, -10)
        every { context.level } returns level
        every { context.clickedPos } returns blockPos
        every { context.horizontalDirection } answers { Direction.fromYRot(player.yRot.toDouble()) }
        every { context.nearestLookingDirections } answers {
            PlayerUtil.transformPlayerTemporarily(player, ship) { Direction.orderedByNearest(player) }
        }
        every { level.getBlockState(any()) } returns Blocks.STONE.defaultBlockState()

        for (block in listOf(Blocks.LEVER, Blocks.STONE_BUTTON)) {
            val state = PlayerUtil.transformPlayerTemporarily(player, ship) { block.getStateForPlacement(context) }!!
            assertEquals(AttachFace.FLOOR, state.getValue(BlockStateProperties.ATTACH_FACE))
            assertEquals(Direction.EAST, state.getValue(BlockStateProperties.HORIZONTAL_FACING))
            assertWorldPose()
        }
    }

    @Test
    fun `nested transforms keep the outer ship pose until the last scope exits`() {
        PlayerUtil.transformPlayerTemporarily(player, ship) {
            val shipPosition = player.position()
            val shipYaw = player.yRot
            val shipPitch = player.xRot
            repeat(2) {
                PlayerUtil.transformPlayerTemporarily(player, ship) {
                    assertEquals(shipPosition, player.position())
                    assertEquals(shipYaw, player.yRot)
                    assertEquals(shipPitch, player.xRot)
                }
                assertEquals(shipPosition, player.position())
                assertEquals(shipYaw, player.yRot)
                assertEquals(shipPitch, player.xRot)
            }
        }
        assertWorldPose()
    }

    @Test
    fun `manual transform pairs preserve an enclosing placement scope`() {
        PlayerUtil.transformPlayerTemporarily(player, ship) {
            val shipPosition = player.position()
            PlayerUtil.transformPlayerTemporarily(player, ship)
            try {
                assertEquals(shipPosition, player.position())
            } finally {
                PlayerUtil.untransformPlayer(player)
            }
            assertEquals(shipPosition, player.position())
            assertEquals(Direction.EAST, Direction.fromYRot(player.yRot.toDouble()))
        }
        assertWorldPose()
    }

    @Test
    fun `nested scope without a ship does not restore the outer pose`() {
        PlayerUtil.transformPlayerTemporarily(player, ship) {
            val shipPosition = player.position()
            PlayerUtil.transformPlayerTemporarily(player, null) {
                assertEquals(shipPosition, player.position())
            }
            assertEquals(shipPosition, player.position())
        }
        assertWorldPose()
    }

    @Test
    fun `a caught nested exception preserves the ship pose and outer cleanup restores world pose`() {
        PlayerUtil.transformPlayerTemporarily(player, ship) {
            val shipPosition = player.position()
            assertThrows(IllegalStateException::class.java) {
                PlayerUtil.transformPlayerTemporarily(player, ship) { error("placement failed") }
            }
            assertEquals(shipPosition, player.position())
        }
        assertWorldPose()

        assertThrows(IllegalStateException::class.java) {
            PlayerUtil.transformPlayerTemporarily(player, ship) {
                PlayerUtil.transformPlayerTemporarily(player, ship) { error("placement failed") }
            }
        }
        assertWorldPose()
    }

    @Test
    fun `world placement and players already in the shipyard keep their pose`() {
        PlayerUtil.transformPlayerTemporarily(player, null) { assertWorldPose() }
        assertWorldPose()

        val shipyardPosition = Vec3(2_000_020.0, 64.0, -10.0)
        position = shipyardPosition
        PlayerUtil.transformPlayerTemporarily(player, ship) {
            assertEquals(shipyardPosition, player.position())
            assertEquals(0.0f, player.yRot)
            assertEquals(60.0f, player.xRot)
        }
        assertEquals(shipyardPosition, player.position())
        assertEquals(15.0f, player.yHeadRot)
    }

    private fun assertWorldPose() {
        assertEquals(worldPosition, player.position())
        assertEquals(0.0f, player.yRot)
        assertEquals(60.0f, player.xRot)
        assertEquals(15.0f, player.yHeadRot)
    }
}
