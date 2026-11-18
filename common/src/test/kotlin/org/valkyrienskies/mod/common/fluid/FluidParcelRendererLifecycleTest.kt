package org.valkyrienskies.mod.common.fluid

import com.mojang.blaze3d.vertex.PoseStack
import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.mod.common.networking.FluidParcelState
import org.valkyrienskies.mod.common.networking.PacketFluidParcel
import java.util.UUID

class FluidParcelRendererLifecycleTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val minecraft = mockk<Minecraft>()
    private val connection = mockk<ClientPacketListener>()
    private val tasks = ArrayList<Runnable>()
    private val state = FluidParcelState("minecraft:water", 0.0, 20.0, 0.0, 0.0, -1.0, 0.0, 0.125)

    @BeforeEach fun setup() {
        mockkStatic(Minecraft::class)
        every { Minecraft.getInstance() } returns minecraft
        every { minecraft.connection } returns connection
        every { minecraft.isPaused } returns false
        every { minecraft.execute(any<Runnable>()) } answers { tasks.add(firstArg()); Unit }
        FluidParcelRenderer.clear()
    }

    @AfterEach fun cleanup() {
        tasks.toList().forEach(Runnable::run)
        FluidParcelRenderer.clear()
        unmockkAll()
    }

    private fun world(): ClientLevel = mockk<ClientLevel>().also {
        every { it.dimension() } returns Level.OVERWORLD
    }

    private fun packet(id: Long) = PacketFluidParcel(UUID(0, id), "minecraft:overworld", 1, state)

    private fun samples(): List<FluidParcelVisuals.Sample> {
        val type = FluidParcelRenderer::class.java
        val cache = type.getDeclaredField("visuals").apply { isAccessible = true }.get(null) as FluidParcelVisuals
        val clock = type.getDeclaredField("clock").apply { isAccessible = true }.get(null) as FluidParcelClock
        return cache.samples(clock.sample(System.nanoTime(), false))
    }

    @Test fun `first frame preserves packets waiting for the client executor`() {
        minecraft.level = world()
        FluidParcelRenderer.enqueue(packet(1), connection)
        FluidParcelRenderer.render(PoseStack(), mockk(), 0.0, 0.0, 0.0)
        assertTrue(samples().isEmpty())
        tasks.removeAt(0).run()
        assertEquals(state, samples().single().state)
    }

    @Test fun `world transition rejects old packets without dropping new world packets`() {
        minecraft.level = world()
        FluidParcelRenderer.enqueue(packet(1), connection)
        minecraft.level = world()
        FluidParcelRenderer.enqueue(packet(2), connection)
        FluidParcelRenderer.render(PoseStack(), mockk(), 0.0, 0.0, 0.0)
        tasks.removeAt(0).run()
        assertEquals(UUID(0, 2), samples().single().id)
    }
}
