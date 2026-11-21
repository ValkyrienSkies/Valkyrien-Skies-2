package org.valkyrienskies.mod.common.schematic

import io.mockk.every
import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.assembly.ICopyableBlock

class DelayedICopyableProcessorTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private abstract class CopyableBlock : Block(Properties.of()), ICopyableBlock

    private val pending = mutableListOf<StructureTemplate.StructureBlockInfo>()
    private val processor = VdexWrapper.DelayedICopyableProcessor(pending)
    private val level = mockk<LevelReader>()

    private fun copyableState(): BlockState {
        val state = mockk<BlockState>()
        every { state.block } returns mockk<CopyableBlock>()
        return state
    }

    private fun process(info: StructureTemplate.StructureBlockInfo) = processor.processBlock(
        level, BlockPos.ZERO, info.pos, info, info, StructurePlaceSettings()
    )

    @Test fun preservesCopyOnlyJointDataAndDefersBlockEntityLoad() {
        val tag = CompoundTag().apply {
            putInt("otherPosXa", 28001234)
            putInt("otherPosYa", 72)
            putInt("otherPosZa", 28005678)
            putLong("otherShipId", 42L)
            putInt("otherPosXr", 900)
        }
        val pos = BlockPos.MutableBlockPos(28002000, 80, 28006000)
        val info = StructureTemplate.StructureBlockInfo(pos, copyableState(), tag)

        val placed = process(info)

        assertNull(placed.nbt)
        assertEquals(info.state, placed.state)
        assertEquals(info.pos, placed.pos)
        assertEquals(1, pending.size)
        assertEquals(tag, pending.single().nbt)
        pos.set(0, 0, 0)
        tag.putInt("otherPosXa", 0)
        assertEquals(BlockPos(28002000, 80, 28006000), pending.single().pos)
        assertEquals(28001234, pending.single().nbt!!.getInt("otherPosXa"))
        assertEquals(42L, pending.single().nbt!!.getLong("otherShipId"))
    }

    @Test fun queuesCopyableBlocksWithoutNbt() {
        val info = StructureTemplate.StructureBlockInfo(BlockPos.ZERO, copyableState(), null)

        assertNull(process(info).nbt)
        assertEquals(1, pending.size)
        assertNull(pending.single().nbt)
    }

    @Test fun leavesOtherBlocksAndTheirNbtUntouched() {
        val info = StructureTemplate.StructureBlockInfo(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), CompoundTag())

        assertSame(info, process(info))
        assertTrue(pending.isEmpty())
    }
}
