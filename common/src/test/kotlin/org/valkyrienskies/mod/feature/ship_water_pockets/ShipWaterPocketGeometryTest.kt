package org.valkyrienskies.mod.feature.ship_water_pockets

import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.computeShapeWaterGeometry

class ShipWaterPocketGeometryTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun closedDoorUsesCollisionShapeInsteadOfFullCubeSeal() {
        val level = mockk<Level>(relaxed = true)
        val pos = BlockPos(0, 64, 0)
        val state = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.OPEN, false)
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)

        val geometry = computeShapeWaterGeometry(level, pos, state)

        assertFalse(geometry.fullSolid)
        assertTrue(geometry.boxes.isNotEmpty())
    }

    @Test
    fun openDoorUsesRefinedGeometryForHoleDetection() {
        val level = mockk<Level>(relaxed = true)
        val pos = BlockPos(0, 64, 0)
        val state = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.OPEN, true)
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)

        val geometry = computeShapeWaterGeometry(level, pos, state)

        assertFalse(geometry.fullSolid)
        assertTrue(geometry.refined)
        assertTrue(geometry.boxes.isNotEmpty())
    }

    @Test
    fun openTrapdoorUsesRefinedGeometryForHoleDetection() {
        val level = mockk<Level>(relaxed = true)
        val pos = BlockPos(0, 64, 0)
        val state = Blocks.OAK_TRAPDOOR.defaultBlockState()
            .setValue(TrapDoorBlock.OPEN, true)
            .setValue(TrapDoorBlock.FACING, Direction.NORTH)

        val geometry = computeShapeWaterGeometry(level, pos, state)

        assertFalse(geometry.fullSolid)
        assertTrue(geometry.refined)
        assertTrue(geometry.boxes.isNotEmpty())
    }
}
