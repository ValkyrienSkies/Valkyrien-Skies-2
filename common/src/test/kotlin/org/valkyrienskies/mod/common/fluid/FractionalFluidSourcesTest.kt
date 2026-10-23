package org.valkyrienskies.mod.common.fluid

import net.minecraft.nbt.ListTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FractionalFluidSourcesTest {
    @Test
    fun `partial source and sub-unit parcel remainder survive reload without rounding`() {
        val sources = FractionalFluidSources()
        assertEquals(255, sources.sourceFillAmount(DIMENSION, 1, 2, 3, WATER))
        val units = BigDecimal("127.0000000000000000000000000000000000001")
        assertTrue(sources.setAmount(DIMENSION, 1, 2, 3, WATER, units))
        val restored = FractionalFluidSources().also { it.load(sources.save()) }
        assertEquals(units, restored.amount(DIMENSION, 1, 2, 3, WATER))
        assertEquals(127, restored.sourceFillAmount(DIMENSION, 1, 2, 3, WATER))
        assertEquals(sources.retainedBytes, restored.retainedBytes)
        assertTrue(restored.hasChunk(DIMENSION, 0, 0, 0))
        assertFalse(restored.hasChunk(DIMENSION, 0, 0, 1))
        val remainder = units - BigDecimal(127)
        assertTrue(restored.setAmount(DIMENSION, 1, 2, 3, WATER, remainder))
        assertEquals(0, restored.sourceFillAmount(DIMENSION, 1, 2, 3, WATER))
        assertEquals(remainder, FractionalFluidSources().also { it.load(restored.save()) }.amount(DIMENSION, 1, 2, 3, WATER))
    }

    @Test
    fun `refill releases ownership and a material replacement clears only its cell`() {
        val sources = FractionalFluidSources()
        assertTrue(sources.setAmount(DIMENSION, 1, 2, 3, WATER, BigDecimal(29)))
        assertTrue(sources.setAmount(DIMENSION, 2, 2, 3, WATER, BigDecimal.ZERO))
        sources.reconcileFluid(DIMENSION, 1, 2, 3, WATER)
        assertEquals(BigDecimal(29), sources.amount(DIMENSION, 1, 2, 3, WATER))
        sources.reconcileFluid(DIMENSION, 1, 2, 3, "minecraft:lava")
        assertNull(sources.amount(DIMENSION, 1, 2, 3, WATER))
        assertEquals(0, sources.sourceFillAmount(DIMENSION, 2, 2, 3, WATER))
        assertTrue(sources.setAmount(DIMENSION, 2, 2, 3, WATER, BigDecimal("255.0")))
        assertEquals(0, sources.size)
        assertEquals(0L, sources.retainedBytes)
    }

    @Test
    fun `capacity refusals and malformed restores preserve all prior ownership`() {
        val sources = FractionalFluidSources(700)
        assertTrue(sources.setAmount(DIMENSION, 1, 2, 3, WATER, BigDecimal(29)))
        val before = sources.save()
        assertFalse(sources.setAmount(DIMENSION, 2, 2, 3, WATER, BigDecimal(29)))
        assertEquals(before, sources.save())
        val duplicate = before.copy()
        val list = duplicate.get("sources") as ListTag
        list.add(list[0].copy())
        assertThrows(IllegalArgumentException::class.java) { sources.load(duplicate) }
        assertEquals(before, sources.save())
        val invalid = before.copy()
        invalid.getList("sources", 10).getCompound(0).putString("units", "1e-2147483647")
        assertThrows(IllegalArgumentException::class.java) { sources.load(invalid) }
        assertEquals(before, sources.save())
    }

    @Test
    fun `source movement conserves the remainder and refuses occupied destinations`() {
        val sources = FractionalFluidSources()
        sources.setAmount(DIMENSION, Int.MIN_VALUE, 2, Int.MAX_VALUE, WATER, BigDecimal(29))
        assertTrue(sources.hasChunk(DIMENSION, -134217728, 0, 134217727))
        assertTrue(sources.move(DIMENSION, Int.MIN_VALUE, 2, Int.MAX_VALUE, "minecraft:the_nether", 4, 5, 6))
        assertNull(sources.amount(DIMENSION, Int.MIN_VALUE, 2, Int.MAX_VALUE, WATER))
        sources.setAmount(DIMENSION, 4, 5, 6, WATER, BigDecimal(7))
        val before = sources.save()
        assertFalse(sources.move("minecraft:the_nether", 4, 5, 6, DIMENSION, 4, 5, 6))
        assertEquals(before, sources.save())
        val restored = FractionalFluidSources().also { it.load(before) }
        assertEquals(BigDecimal(29), restored.amount("minecraft:the_nether", 4, 5, 6, WATER))
        assertEquals(BigDecimal(7), restored.amount(DIMENSION, 4, 5, 6, WATER))
    }

    private companion object {
        const val DIMENSION = "minecraft:overworld"
        const val WATER = "minecraft:water"
    }
}
