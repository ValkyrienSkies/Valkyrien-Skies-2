package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.physics.*
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import java.util.UUID

class RetainedFluidBlockInteractionsTest {
    companion object {
        private const val DIMENSION = "minecraft:overworld"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>(relaxed = true)
    private val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
    private val world = mockk<VsiServerShipWorld>(relaxed = true)
    private val ship = mockk<ServerShip>()
    private var manager = RetainedFluidBlockInteractions()
    private val states = HashMap<BlockPos, BlockState>()
    private val requests = LinkedHashMap<UUID, VsiFluidAbsorption>()
    private val receipts = HashMap<UUID, VsiFluidAbsorptionResult>()
    private val levels get() = mapOf(DIMENSION to level)
    private val sponge = BlockPos(1, 0, 0)
    private var snapshot = snapshot(31.875)

    private fun snapshot(amount: Double, count: Int = 1, sequence: Long = 1): VsiFluidGameplaySnapshot {
        val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
        val voxels = (0 until count).map { x -> VsiFluidFloodedVoxel(x, 0, 0, 17, amount.toInt(), 0,
            surfaceRegions = listOf(VsiFluidSurfaceRegion(bounds, 0.0, 1.0, 0.0, 0.0, amount / 255,
                listOf(VsiFluidSurfaceConstituent(17, amount))))) }
        return VsiFluidGameplaySnapshot(VsiFluidTopologySnapshot(19, DIMENSION, sequence, 3, 4,
            true, true, 0, emptyList()), VsiFluidFloodingSnapshot(19, DIMENSION, sequence, 3, 4, count, voxels), true)
    }

    @BeforeEach fun setup() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        mockkObject(MassDatapackResolver)
        val water = mockk<FlowingFluid>()
        val waterState = mockk<FluidState>()
        every { water.defaultFluidState() } returns waterState
        every { waterState.`is`(FluidTags.WATER) } returns true
        every { waterState.`is`(FluidTags.LAVA) } returns false
        every { MassDatapackResolver.getFlowingFluid(17) } returns water
        every { MassDatapackResolver.fluidTypesForClient() } returns mapOf(17 to "minecraft:water")
        every { level.dimensionId } returns DIMENSION
        every { level.getShipManagingPos(any<BlockPos>()) } returns ship
        every { ship.id } returns 19
        every { level.server } returns server
        every { (server as IShipObjectWorldServerProvider).shipObjectWorld } returns world
        every { (server as IShipObjectWorldServerProvider).fluidBlockInteractions } answers { manager }
        every { level.hasChunkAt(any()) } returns true
        every { level.getBlockState(any()) } answers { states[firstArg()] ?: Blocks.AIR.defaultBlockState() }
        every { level.setBlock(any(), any(), any()) } answers { states[firstArg()] = secondArg(); true }
        every { world.supportsFluidAbsorption } returns true
        every { world.getFluidGameplaySnapshot(19) } answers { snapshot }
        every { world.submitFluidAbsorption(any()) } answers {
            firstArg<VsiFluidAbsorption>().let { requests[it.id] = it }; true
        }
        every { world.hasFluidAbsorption(any()) } answers { requests.containsKey(firstArg()) }
        every { world.cancelFluidAbsorption(any()) } answers {
            val id = firstArg<UUID>()
            if (receipts.containsKey(id)) false else { requests.remove(id); true }
        }
        every { world.consumeFluidAbsorption(any(), any()) } answers {
            val id = firstArg<UUID>()
            receipts[id]?.let {
                secondArg<(VsiFluidAbsorptionResult) -> Unit>().invoke(it)
                receipts.remove(id); requests.remove(id); true
            } ?: false
        }
    }

    @AfterEach fun cleanup() { ShipFluidInteraction.clear(world); unmockkAll() }

    @Test fun `breakables use water threshold and ordinary block destruction`() {
        states[BlockPos.ZERO] = Blocks.WHEAT.defaultBlockState()
        snapshot = snapshot(Math.nextDown(31.875))
        manager.onSnapshot(snapshot); manager.tick(world, levels)
        verify(exactly = 0) { level.destroyBlock(any(), any<Boolean>()) }
        snapshot = snapshot(31.875, sequence = 2)
        manager.onSnapshot(snapshot); manager.tick(world, levels)
        verify(exactly = 1) { level.destroyBlock(BlockPos.ZERO, true) }
        assertTrue(requests.isEmpty())
    }

    @Test fun `water resistant and waterloggable blocks are protected`() {
        for (block in listOf(Blocks.OAK_DOOR, Blocks.OAK_SIGN, Blocks.LADDER, Blocks.SUGAR_CANE,
                Blocks.BUBBLE_COLUMN, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY,
                Blocks.STRUCTURE_VOID, Blocks.RAIL, Blocks.OAK_SLAB, Blocks.WATER))
            assertFalse(RetainedFluidBlockInteractions.isWaterBreakable(block.defaultBlockState()), block.toString())
        for (block in listOf(Blocks.WHEAT, Blocks.CARROTS, Blocks.REDSTONE_WIRE, Blocks.TORCH, Blocks.FIRE))
            assertTrue(RetainedFluidBlockInteractions.isWaterBreakable(block.defaultBlockState()), block.toString())
    }

    @Test fun `dry farmland absorbs a shallow contacting cell and wets once after reload`() {
        val farm = BlockPos.ZERO.below()
        states[farm] = Blocks.FARMLAND.defaultBlockState()
        snapshot = snapshot(0.25)
        assertTrue(RetainedFluidBlockInteractions.requestFarmland(level, farm))
        assertTrue(RetainedFluidBlockInteractions.requestFarmland(level, farm))
        val request = requests.values.single()
        assertTrue(request.shallowCell)
        assertEquals(0, request.originY)
        assertEquals(Math.nextDown(255.0 / 8), request.maximumAmount)
        assertEquals(0, states.getValue(farm).getValue(FarmBlock.MOISTURE))
        manager = RetainedFluidBlockInteractions().also { it.load(manager.save()) }
        receipts[request.id] = VsiFluidAbsorptionResult(0.25, 1)
        manager.tick(world, levels)
        manager.tick(world, levels)
        assertEquals(7, states.getValue(farm).getValue(FarmBlock.MOISTURE))
        verify(exactly = 1) { level.setBlock(farm, any(), 2) }
        assertTrue(requests.isEmpty())
    }

    @Test fun `farmland leaves water at the destruction threshold for ordinary hydration`() {
        val farm = BlockPos.ZERO.below()
        states[farm] = Blocks.FARMLAND.defaultBlockState()
        assertFalse(RetainedFluidBlockInteractions.requestFarmland(level, farm))
        assertTrue(requests.isEmpty())
    }

    @Test fun `shallow water extinguishes a campfire only after a durable debit`() {
        states[BlockPos.ZERO] = Blocks.CAMPFIRE.defaultBlockState()
        snapshot = snapshot(.25)
        manager.onSnapshot(snapshot)
        manager.tick(world, levels)
        val request = requests.values.single()
        assertTrue(request.shallowCell)
        assertTrue(states.getValue(BlockPos.ZERO).getValue(BlockStateProperties.LIT))
        manager = RetainedFluidBlockInteractions().also { it.load(manager.save()) }
        receipts[request.id] = VsiFluidAbsorptionResult(.25, 1)
        manager.tick(world, levels)
        assertFalse(states.getValue(BlockPos.ZERO).getValue(BlockStateProperties.LIT))
        assertFalse(states.getValue(BlockPos.ZERO).getValue(BlockStateProperties.WATERLOGGED))
        assertTrue(requests.isEmpty())
    }

    @Test fun `water at the cutoff extinguishes a campfire without a debit`() {
        states[BlockPos.ZERO] = Blocks.SOUL_CAMPFIRE.defaultBlockState()
        manager.onSnapshot(snapshot)
        manager.tick(world, levels)
        assertFalse(states.getValue(BlockPos.ZERO).getValue(BlockStateProperties.LIT))
        assertTrue(requests.isEmpty())
    }

    @Test fun `fractional absorption wets once after durable result survives reload`() {
        states[sponge] = Blocks.SPONGE.defaultBlockState()
        RetainedFluidBlockInteractions.requestSponge(level, sponge)
        RetainedFluidBlockInteractions.requestSponge(level, sponge)
        val request = requests.values.single()
        assertEquals(listOf(17), request.allowedFluidIds)
        assertEquals(6, request.maximumDepth)
        assertEquals(64, request.maximumCells)
        assertEquals(16320.0, request.maximumAmount)
        assertTrue(states[sponge]!!.`is`(Blocks.SPONGE))
        manager = RetainedFluidBlockInteractions().also { it.load(manager.save()) }
        receipts[request.id] = VsiFluidAbsorptionResult(.25, 1)
        manager.tick(world, levels); manager.tick(world, levels)
        assertTrue(states[sponge]!!.`is`(Blocks.WET_SPONGE))
        verify(exactly = 1) { level.setBlock(sponge, Blocks.WET_SPONGE.defaultBlockState(), 2) }
        assertTrue(requests.isEmpty())
    }

    @Test fun `replaced sponge cannot inherit a previous placement completion after reload`() {
        states[sponge] = Blocks.SPONGE.defaultBlockState()
        RetainedFluidBlockInteractions.requestSponge(level, sponge)
        val request = requests.values.single()
        receipts[request.id] = VsiFluidAbsorptionResult(10.0, 1)
        RetainedFluidBlockInteractions.onBlockChanged(level, sponge, states[sponge]!!, Blocks.AIR.defaultBlockState())
        manager = RetainedFluidBlockInteractions().also { it.load(manager.save()) }
        manager.tick(world, levels)
        assertTrue(states[sponge]!!.`is`(Blocks.SPONGE))
        assertNotEquals(request.id, requests.values.single().id)
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
    }

    @Test fun `unloaded chunk waits without consuming successful receipt`() {
        states[sponge] = Blocks.SPONGE.defaultBlockState()
        RetainedFluidBlockInteractions.requestSponge(level, sponge)
        val request = requests.values.single()
        receipts[request.id] = VsiFluidAbsorptionResult(1.0, 1)
        every { level.hasChunkAt(sponge) } returns false
        manager.tick(world, levels)
        assertEquals(1, receipts.size)
        every { level.hasChunkAt(sponge) } returns true
        manager.tick(world, levels)
        assertTrue(states[sponge]!!.`is`(Blocks.WET_SPONGE))
    }

    @Test fun `bounded scan progresses across changing publications`() {
        snapshot = snapshot(31.875, 300)
        for (x in 0 until 300) states[BlockPos(x, 0, 0)] = Blocks.WHEAT.defaultBlockState()
        manager.onSnapshot(snapshot); manager.tick(world, levels)
        verify(exactly = 256) { level.destroyBlock(any(), true) }
        snapshot = snapshot(31.875, 300, 2)
        manager.onSnapshot(snapshot); manager.tick(world, levels)
        verify(exactly = 300) { level.destroyBlock(any(), true) }
    }

    @Test fun `stale zero receipt retries against a newer publication`() {
        states[sponge] = Blocks.SPONGE.defaultBlockState()
        RetainedFluidBlockInteractions.requestSponge(level, sponge)
        val first = requests.values.single()
        snapshot = snapshot(31.875, sequence = 2)
        manager.onSnapshot(snapshot); manager.tick(world, levels)
        receipts[first.id] = VsiFluidAbsorptionResult(0.0, 0)
        manager.tick(world, levels)
        assertNotEquals(first.id, requests.values.single().id)
        verify(exactly = 2) { world.submitFluidAbsorption(any()) }
    }

    @Test fun `dry zero receipt waits for water changes instead of resubmitting every tick`() {
        states[sponge] = Blocks.SPONGE.defaultBlockState()
        RetainedFluidBlockInteractions.requestSponge(level, sponge)
        val first = requests.values.single()
        receipts[first.id] = VsiFluidAbsorptionResult(0.0, 0)
        repeat(3) { manager.tick(world, levels) }
        assertTrue(requests.isEmpty())
        verify(exactly = 1) { world.submitFluidAbsorption(any()) }
    }
}
