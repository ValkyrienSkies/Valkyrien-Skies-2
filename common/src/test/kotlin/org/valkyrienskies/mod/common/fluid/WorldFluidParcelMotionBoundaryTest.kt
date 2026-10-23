package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import org.joml.Vector3d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.mod.common.config.MassDatapackResolver

class WorldFluidParcelMotionBoundaryTest {
    companion object {
        private const val DIMENSION = "minecraft:overworld"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>()
    private var loaded: (BlockPos) -> Boolean = { true }
    private val levels get() = mapOf(DIMENSION to level)

    @BeforeEach fun setup() {
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } answers { loaded(firstArg()) }
        every { level.getBlockState(any()) } returns Blocks.AIR.defaultBlockState()
        every { level.players() } returns emptyList()
    }

    @AfterEach fun cleanup() { unmockkAll() }

    private fun enqueue(manager: WorldFluidOutflowManager, position: Vector3d, velocity: Vector3d) {
        val event = VsiFluidOutflow(1, 1, 1, DIMENSION, 7, position.x, position.y, position.z,
            0.0, -1.0, 0.0, velocity.x, velocity.y, velocity.z, 17, 510)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, event))
    }

    private fun entry(manager: WorldFluidOutflowManager): CompoundTag {
        val entries = manager.save().getList("parcels", Tag.TAG_COMPOUND.toInt())
        assertEquals(1, entries.size)
        return entries.getCompound(0)
    }

    private fun assertMotion(saved: CompoundTag, position: Vector3d, velocity: Vector3d, context: String) {
        for ((key, expected) in listOf("x" to position.x, "y" to position.y, "z" to position.z,
            "vx" to velocity.x, "vy" to velocity.y, "vz" to velocity.z)) {
            // Minecraft's DoubleTag.valueOf canonicalizes either signed zero to +0.0.
            // Compare the serialized helper result exactly, without relaxing nonzero bits.
            val expectedTag = CompoundTag().apply { putDouble(key, expected) }
            assertEquals(expectedTag.getDouble(key).toBits(), saved.getDouble(key).toBits(), "$context $key")
        }
        assertEquals(0L, saved.getLong("lag"), context)
        assertEquals(2.0, saved.getString("volume").toDouble(), context)
    }

    @Test fun `entry validation and private motion match the general helper across pressure and restore`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE)) for (axis in 0..2)
            for (pressure in listOf(false, true)) {
                clearMocks(level, answers = false, recordedCalls = true, childMocks = false)
                var now = 0L
                val initialVelocity = Vector3d(3.0, 4.0, 5.0).setComponent(axis, value)
                val position = Vector3d(0.5, 100.5, 0.5)
                val velocity = Vector3d(initialVelocity)
                var manager = WorldFluidOutflowManager(1, 1) { now }
                enqueue(manager, position, initialVelocity)
                if (!velocity.x.isFinite() || !velocity.y.isFinite() || !velocity.z.isFinite()) velocity.zero()
                assertMotion(entry(manager), position, velocity, "initial $axis $value")
                if (pressure) manager.servicePressure(levels)
                repeat(24) { tick ->
                    position.add(fluidParcelTickDisplacement(velocity))
                    if (pressure) { now += 50_000_000L; manager.servicePressure(levels) }
                    else manager.tick(levels)
                    assertMotion(entry(manager), position, velocity, "$axis $value pressure=$pressure tick=$tick")
                    if (tick == 11) {
                        val saved = manager.save()
                        manager = WorldFluidOutflowManager(1, 1) { now }.also { it.load(saved) }
                        if (pressure) manager.servicePressure(levels)
                    }
                }
            }
    }

    @Test fun `exact cell faces and nearest reachable endpoints never bypass unloaded terrain`() {
        val gravityPerTick = -9.81 * (1.0 / 20.0)
        for (origin in listOf(-0.5, 0.5)) for (axis in 0..2) for (direction in listOf(-1, 1))
            for (edge in -1..1) for (pressure in listOf(false, true)) {
                clearMocks(level, answers = false, recordedCalls = true, childMocks = false)
                val position = Vector3d(origin, 100.5, origin)
                val cell = kotlin.math.floor(position.get(axis)).toInt()
                loaded = { pos -> (when (axis) { 0 -> pos.x; 1 -> pos.y; else -> pos.z }) == cell }
                val velocity = Vector3d(0.0, -gravityPerTick, 0.0)
                val face = position.get(axis) + direction * 0.5
                val atFace = direction * 10.0 - if (axis == 1) gravityPerTick else 0.0
                fun endpoint(v: Double): Double =
                    fluidParcelTickDisplacement(Vector3d(velocity).setComponent(axis, v)).get(axis) + position.get(axis)
                assertEquals(face, endpoint(atFace), "Exact face fixture")
                var chosen = atFace
                if (edge != 0) {
                    var attempts = 0
                    while (endpoint(chosen) == face && attempts++ < 1024) {
                        chosen = if (edge < 0) Math.nextDown(chosen) else Math.nextUp(chosen)
                    }
                    assertTrue(attempts < 1024)
                    assertTrue(if (edge < 0) endpoint(chosen) < face else endpoint(chosen) > face)
                }
                velocity.setComponent(axis, chosen)
                val movedVelocity = Vector3d(velocity)
                val movedPosition = Vector3d(position).add(fluidParcelTickDisplacement(movedVelocity))
                val crosses = movedPosition.get(axis) < cell.toDouble() || movedPosition.get(axis) >= cell.toDouble() + 1.0
                var now = 0L
                val manager = WorldFluidOutflowManager(1, 1) { now }
                enqueue(manager, position, velocity)
                if (pressure) { manager.servicePressure(levels); now += 50_000_000L; manager.servicePressure(levels) }
                else manager.tick(levels)
                assertMotion(entry(manager), if (crosses) position else movedPosition,
                    if (crosses) velocity else movedVelocity, "$origin axis=$axis direction=$direction edge=$edge pressure=$pressure")
            }
    }
}
