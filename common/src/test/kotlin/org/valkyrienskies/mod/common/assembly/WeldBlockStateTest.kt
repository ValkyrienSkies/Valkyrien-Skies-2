package org.valkyrienskies.mod.common.assembly

import net.minecraft.SharedConstants
import net.minecraft.core.Direction
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WeldBlockStateTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test fun cubeAndLogCanBeAlignedToAnyFace() {
        for (rotation in WeldRotation.ALL) {
            assertEquals(Blocks.STONE.defaultBlockState(), rotation.rotateState(Blocks.STONE.defaultBlockState()))
            val log = rotation.rotateState(Blocks.OAK_LOG.defaultBlockState())!!
            assertEquals(rotation.yAxis.axis, log.getValue(BlockStateProperties.AXIS))
        }
    }

    @Test fun directionalBlocksRotateAllSixDirections() {
        for (rotation in WeldRotation.ALL) for (direction in Direction.values()) {
            val state = Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, direction)
            val result = rotation.rotateState(state)!!
            assertEquals(rotation.transform(direction), result.getValue(BlockStateProperties.FACING))
        }
    }

    @Test fun stairsAndDoorsUseMinecraftYawRotationAndRejectTilting() {
        for (rotation in WeldRotation.ALL) {
            for (block in listOf(Blocks.OAK_STAIRS, Blocks.OAK_DOOR, Blocks.GLASS_PANE, Blocks.RAIL)) {
                val state = block.defaultBlockState()
                if (rotation.yaw == null) assertNull(rotation.rotateState(state))
                else assertEquals(state.rotate(rotation.yaw!!), rotation.rotateState(state))
            }
        }
    }
}
