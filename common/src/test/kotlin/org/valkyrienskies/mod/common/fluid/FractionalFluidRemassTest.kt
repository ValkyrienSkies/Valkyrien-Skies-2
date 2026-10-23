package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import org.joml.primitives.AABBi
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import java.math.BigDecimal

class FractionalFluidRemassTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    @Test fun `mass recalculation rebuilds the saved source quantity without debiting its carrier`() {
        val registry = BlockStateInfo.javaClass.getDeclaredField("SORTED_REGISTRY").apply { isAccessible = true }
        val previousRegistry = registry.get(null)
        try {
            registry.set(null, emptyList<Any>())
            mockkObject(BlockStateInfo)
            mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val level = mockk<ServerLevel>()
            val world = mockk<VsiServerShipWorld>(relaxed = true)
            val ship = mockk<LoadedServerShip>(relaxed = true)
            val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
            val air = mockk<VsiBlockType>()
            val water = mockk<VsiBlockType>()
            val sources = FractionalFluidSources().apply {
                setAmount("water", 0, 0, 0, "minecraft:water", BigDecimal("127.25"))
            }
            every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } returns sources
            every { level.server } returns server
            every { level.dimensionId } returns "water"
            every { level.shipObjectWorld } returns world
            every { level.getBlockState(BlockPos.ZERO) } returns Blocks.WATER.defaultBlockState()
            every { ship.shipAABB } returns AABBi(0, 0, 0, 0, 0, 0)
            every { ship.isStatic } returns false
            every { ship.inertiaData.mass } returns 100.0
            every { BlockStateInfo.get(Blocks.AIR.defaultBlockState()) } returns (0.0 to air)
            every { BlockStateInfo.get(Blocks.WATER.defaultBlockState()) } returns (2.0 to water)
            assertTrue(BlockStateInfo.remassShip(level, ship))
            verify(exactly = 1) { world.onSetBlock(0, 0, 0, "water", air, water, 0.0, 2.0, 127) }
            verify(exactly = 0) { world.onSetBlock(0, 0, 0, "water", air, water, 0.0, 2.0) }
            assertEquals(BigDecimal("127.25"), sources.amount("water", 0, 0, 0, "minecraft:water"))
            verify { ship.isStatic = false }
        } finally {
            unmockkAll()
            registry.set(null, previousRegistry)
        }
    }
}
