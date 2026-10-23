package org.valkyrienskies.mod.forge

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.mockk.*
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import java.util.function.BiConsumer
import java.util.function.Predicate
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.fluids.FluidType
import net.minecraftforge.network.NetworkHooks
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.mod.common.fluid.ShipFluidInteraction
import org.valkyrienskies.mod.forge.mixin.feature.water_in_ships_entity.MixinEntity

class FluidEntityHookTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            // Plain JVM tests lack Forge's event-bus bytecode transformer; no networking is used by these hooks.
            mockkStatic(NetworkHooks::class)
            every { NetworkHooks.init() } just Runs
            try { Bootstrap.bootStrap() } finally { unmockkStatic(NetworkHooks::class) }
        }
    }
    private val entity = object : MixinEntity() {
        override fun getX() = 0.5
        override fun getZ() = 0.5
        override fun getEyeY() = 1.5
        override fun touchingUnloadedChunk() = false
    }
    private val level = mockk<Level>()
    private val query = mockk<ShipFluidInteraction.VolumeQuery>()
    private val heights = Object2DoubleOpenHashMap<FluidType>()
    private val waterType = mockk<FluidType>()
    private val waterState = mockk<FluidState>()

    @BeforeEach fun setup() {
        entity.level = level
        field("bb", AABB(0.1, 0.0, 0.1, 0.9, 1.8, 0.9))
        field("forgeFluidTypeHeight", heights)
        mockkStatic(ShipFluidInteraction::class)
        every { ShipFluidInteraction.prepareVolume(level, any()) } returns query
        every { waterState.fluidType } returns waterType
        every { waterType.isAir } returns false
        every { query.forEachRetained(any()) } answers {
            firstArg<BiConsumer<FluidState, Double>>().accept(waterState, 0.4)
        }
    }
    @AfterEach fun cleanup() = unmockkAll()
    private fun field(name: String, value: Any?) = MixinEntity::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.set(entity, value)
    private fun invoke(name: String, vararg args: Any): Any? = MixinEntity::class.java.declaredMethods
        .single { it.name == name }.apply { isAccessible = true }.invoke(entity, *args)

    @Test fun `real Forge type map merges retained depth after the original flow loop and respects its predicate`() {
        var calls = 0
        val original = Operation<Void> { calls++; heights.put(waterType, 0.8); null }
        invoke("vs\$mergeRetainedFluid", Predicate<FluidState> { true }, original)
        assertEquals(1, calls)
        assertEquals(0.8, heights.getDouble(waterType))
        val dryWorld = Operation<Void> { heights.clear(); null }
        invoke("vs\$mergeRetainedFluid", Predicate<FluidState> { true }, dryWorld)
        assertEquals(0.4, heights.getDouble(waterType))
        invoke("vs\$mergeRetainedFluid", Predicate<FluidState> { false }, dryWorld)
        assertTrue(heights.isEmpty())
    }

    @Test fun `world cell clips only controlled portions`() {
        field("vs\$fluidQuery", query)
        every { query.hasDomains() } returns true
        every { waterState.isEmpty } returns false
        every { waterState.getHeight(level, BlockPos.ZERO) } returns 1.0f
        every { query.worldHeight(BlockPos.ZERO, 1.0f) } returns 0.35f
        val original = Operation<FluidState> { waterState }
        assertSame(waterState, invoke("valkyrienskies\$pushFluidState", level, BlockPos.ZERO, original))
        assertEquals(0.35f, invoke("valkyrienskies\$pushHeight", waterState, level, BlockPos.ZERO, Operation<Float> { 1.0f }))
        val current = Vec3(0.2, 0.0, -0.3)
        assertSame(current, invoke("valkyrienskies\$pushFlow", waterState, level, BlockPos.ZERO, Operation<Vec3> { current }))
        every { query.worldHeight(BlockPos.ZERO, 1.0f) } returns Float.NaN
        assertTrue((invoke("valkyrienskies\$pushFluidState", level, BlockPos.ZERO, original) as FluidState).isEmpty)
    }

    @Test fun `dry eye hook does not erase whole body Forge immersion`() {
        heights.put(waterType, 0.7)
        every { ShipFluidInteraction.samplePoint(level, any()) } returns ShipFluidInteraction.PointSample(true, null)
        val result = invoke("getFluidStateRedirect", level, BlockPos.ZERO, Operation<FluidState> { Fluids.WATER.defaultFluidState() }) as FluidState
        assertTrue(result.isEmpty)
        assertEquals(0.7, heights.getDouble(waterType))
        every { ShipFluidInteraction.samplePoint(level, any()) } returns ShipFluidInteraction.PointSample(true, Fluids.WATER)
        val retained = invoke("getFluidStateRedirect", level, BlockPos.ZERO, Operation<FluidState> { result }) as FluidState
        assertSame(Fluids.WATER, retained.type)
        assertEquals(Float.POSITIVE_INFINITY, invoke("fluidHeightOverride", retained, level, BlockPos.ZERO, Operation<Float> { 0.0f }))
    }
}
