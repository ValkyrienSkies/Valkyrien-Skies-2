package org.valkyrienskies.mod.common.config

import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DecorativeBlockCollisionTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test fun everyTorchStateHasEmptyPhysicsCollisionGeometry() {
        val world = object : BlockGetter {
            override fun getHeight() = 384
            override fun getMinBuildHeight() = -64
            override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
            override fun getBlockState(pos: BlockPos) = Blocks.AIR.defaultBlockState()
            override fun getFluidState(pos: BlockPos) = Fluids.EMPTY.defaultFluidState()
        }
        for (block in listOf(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH)) {
            for (state in block.stateDefinition.possibleStates) {
                assertFalse(state.isSolid, "Torch classified as a solid block: $state")
                assertTrue(state.getCollisionShape(world, BlockPos.ZERO).isEmpty,
                    "Nonempty Minecraft collision shape for $state")
            }
        }
    }
}
