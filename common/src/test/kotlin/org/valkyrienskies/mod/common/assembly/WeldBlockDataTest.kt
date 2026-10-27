package org.valkyrienskies.mod.common.assembly

import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WeldBlockDataTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test fun containerContentsAndOriginalBackupSurviveRelocation() {
        val from = BlockPos(28672010, 130, -28672030)
        val to = BlockPos(28676010, 70, -28676030)
        val source = ChestBlockEntity(from, Blocks.CHEST.defaultBlockState())
        source.setItem(7, ItemStack(Items.DIAMOND, 37))
        val original = source.saveWithFullMetadata()
        val pasted = WeldBlockData.relocatedTag(original, to)
        val destination = ChestBlockEntity(to, Blocks.CHEST.defaultBlockState())
        destination.load(pasted)
        assertEquals(37, destination.getItem(7).count)
        assertEquals(Items.DIAMOND, destination.getItem(7).item)
        assertEquals(37, source.getItem(7).count)
        assertEquals(from.x, original.getInt("x"))
        assertEquals(to.x, pasted.getInt("x"))
        assertEquals(to.y, pasted.getInt("y"))
        assertEquals(to.z, pasted.getInt("z"))
        destination.getItem(7).count = 1
        val rollback = ChestBlockEntity(from, Blocks.CHEST.defaultBlockState())
        rollback.load(original)
        assertEquals(37, rollback.getItem(7).count)
    }
}
