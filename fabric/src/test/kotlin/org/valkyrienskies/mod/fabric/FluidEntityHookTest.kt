package org.valkyrienskies.mod.fabric

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.mockk.*
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.mod.common.fluid.ShipFluidInteraction
import org.valkyrienskies.mod.fabric.mixin.feature.water_in_ships_entity.MixinEntity

class FluidEntityHookTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val entity = object : MixinEntity() {
        override fun getX() = 0.5
        override fun getZ() = 0.5
        override fun getEyeY() = 1.5
        override fun touchingUnloadedChunk() = false
    }
    private val level = mockk<Level>()
    private val query = mockk<ShipFluidInteraction.VolumeQuery>()
    private val heights = Object2DoubleOpenHashMap<TagKey<Fluid>>()

    @BeforeEach fun setup() {
        entity.level = level
        field("bb", AABB(0.1, 0.0, 0.1, 0.9, 1.8, 0.9))
        field("fluidHeight", heights)
        mockkStatic(ShipFluidInteraction::class)
        every { ShipFluidInteraction.prepareVolume(level, any()) } returns query
        every { query.retainedDepth(FluidTags.WATER) } returns 0.4
    }
    @AfterEach fun cleanup() = unmockkAll()
    private fun field(name: String, value: Any?) = MixinEntity::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.set(entity, value)
    private fun invoke(name: String, vararg args: Any): Any? = MixinEntity::class.java.declaredMethods
        .single { it.name == name }.apply { isAccessible = true }.invoke(entity, *args)

    @Test fun `original world flow runs and retained depth merges without replacing greater world immersion`() {
        var calls = 0
        val original = Operation<Boolean> {
            calls++
            heights.put(FluidTags.WATER, 0.8)
            true
        }
        assertEquals(true, invoke("vs\$mergeRetainedFluid", FluidTags.WATER, 0.014, original))
        assertEquals(1, calls)
        assertEquals(0.8, heights.getDouble(FluidTags.WATER))
        val dryWorld = Operation<Boolean> { heights.put(FluidTags.WATER, 0.0); false }
        assertEquals(true, invoke("vs\$mergeRetainedFluid", FluidTags.WATER, 0.014, dryWorld))
        assertEquals(0.4, heights.getDouble(FluidTags.WATER))
    }

    @Test fun `partial world cell keeps its real fluid and height while fully covered cell is empty`() {
        field("vs\$fluidQuery", query)
        every { query.hasDomains() } returns true
        val water = Fluids.WATER.defaultFluidState()
        every { level.getFluidState(any()) } returns Fluids.EMPTY.defaultFluidState()
        every { query.worldHeight(BlockPos.ZERO, any()) } returns 0.35f
        val original = Operation<FluidState> { water }
        assertSame(water, invoke("valkyrienskies\$pushFluidState", level, BlockPos.ZERO, original))
        assertEquals(0.35f, invoke("valkyrienskies\$pushHeight", water, level, BlockPos.ZERO, Operation<Float> { error("Clipped height required") }))
        val current = Vec3(0.2, 0.0, -0.3)
        assertSame(current, invoke("valkyrienskies\$pushFlow", water, level, BlockPos.ZERO, Operation<Vec3> { current }))
        every { query.worldHeight(BlockPos.ZERO, any()) } returns Float.NaN
        assertTrue((invoke("valkyrienskies\$pushFluidState", level, BlockPos.ZERO, original) as FluidState).isEmpty)
    }

    @Test fun `dry eyes preserve body heights and retained eyes supply platform state`() {
        heights.put(FluidTags.WATER, 0.7)
        val empty = Fluids.EMPTY.defaultFluidState()
        val original = Operation<FluidState> { Fluids.LAVA.defaultFluidState() }
        every { ShipFluidInteraction.samplePoint(level, any()) } returns ShipFluidInteraction.PointSample(true, null)
        assertSame(empty, invoke("getFluidStateRedirect", level, BlockPos.ZERO, original))
        assertEquals(0.7, heights.getDouble(FluidTags.WATER))
        every { ShipFluidInteraction.samplePoint(level, any()) } returns ShipFluidInteraction.PointSample(true, Fluids.WATER)
        val water = invoke("getFluidStateRedirect", level, BlockPos.ZERO, original) as FluidState
        assertSame(Fluids.WATER, water.type)
        assertEquals(Float.POSITIVE_INFINITY, invoke("fluidHeightOverride", water, level, BlockPos.ZERO, Operation<Float> { 0.0f }))
    }
}
