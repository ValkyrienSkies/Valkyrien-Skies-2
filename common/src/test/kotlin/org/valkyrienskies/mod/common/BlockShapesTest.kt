package org.valkyrienskies.mod.common

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.material.Fluids
import org.joml.Vector3i
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.valkyrienskies.core.api.physics.blockstates.BoxesBlockShape
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.core.internal.VsiCoreFactory
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksOut
import org.valkyrienskies.core.internal.VsiCoreServer
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.VsiShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate
import org.valkyrienskies.mod.api.VsShapedBlock
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources
import java.nio.file.Path

/**
 * Blocks whose collision shape lives in a block entity ([VsShapedBlock]) register that shape with
 * the physics engine when it is met, and every place VS hands a block's type to the engine has to
 * hand over the registered one instead of the block state's. Runs against a real VS-Core, because
 * what is being checked is that a late registration comes back as a usable, interned type.
 */
class BlockShapesTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @TempDir lateinit var configDir: Path
    private lateinit var core: VsiCoreServer

    /** A panel: its shape is whatever [shapes] says for its position. */
    private val shapes = HashMap<BlockPos, List<IntArray>?>()
    private val panelBlock = mockk<Block>(moreInterfaces = arrayOf(VsShapedBlock::class))
    private val panel = mockk<BlockState>(relaxed = true)
    private val stone: BlockState = Blocks.STONE.defaultBlockState()
    private val stoneMass = 1000.0
    private val panelMass = 50.0
    private lateinit var stoneType: VsiBlockType
    private lateinit var panelStateType: VsiBlockType

    @BeforeEach fun setUp() {
        val hooks = mockk<VsiCoreHooksOut>(relaxed = true) {
            every { isPhysicalClient } returns false
            every { configDir } returns this@BlockShapesTest.configDir
        }
        core = VsiCoreFactory.instance.newVsCoreServer(hooks)
        core.registerBlockStates(emptyList())
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { vsCore } returns core
        stoneType = core.blockTypes.solid
        panelStateType = core.blockTypes.solid

        every { panel.block } returns panelBlock
        every { panel.fluidState } returns Fluids.EMPTY.defaultFluidState()
        every { (panelBlock as VsShapedBlock).getPhysicsSolidState(any(), any(), any()) } answers {
            shapes[secondArg()]?.let { BlockShapes.solidFromBoxes(it, 0.5, 0.0, 1.0) }
        }
    }

    @AfterEach fun tearDown() = unmockkAll()

    private fun plate(thickness: Int) = listOf(intArrayOf(0, 0, 0, 16, thickness, 16))

    private fun boxesOf(type: VsiBlockType): List<Pair<IntArray, IntArray>> {
        val shape = core.blockTypes.getState(type)!!.solidState!!.shape as BoxesBlockShape
        return shape.positiveBoxes.map {
            intArrayOf(it.minX(), it.minY(), it.minZ()) to intArrayOf(it.maxX(), it.maxY(), it.maxZ())
        }
    }

    @Test fun `a plate keeps its boxes and an emptied panel is an empty shape rather than no answer`() {
        val solid = BlockShapes.solidFromBoxes(plate(4), 0.5, 0.0, 1.0)!!
        val shape = solid.shape as BoxesBlockShape
        assertEquals(1, shape.positiveBoxes.size)
        val box = shape.positiveBoxes.single()
        assertEquals(listOf(0, 0, 0, 15, 3, 15), listOf(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
        assertTrue(shape.negativeBoxes.isNotEmpty())

        // Null means "use the block state's own", which is a whole cube; a panel beaten empty is not that.
        val empty = BlockShapes.solidFromBoxes(emptyList(), 0.5, 0.0, 1.0)
        assertNotNull(empty)
        assertTrue((empty!!.shape as BoxesBlockShape).positiveBoxes.isEmpty())
    }

    @Test fun `a shape too broken up for the engine is refused rather than sent`() {
        // Twelve separate posts on a 16-grid cannot merge below the engine's ten-box limit.
        val posts = (0 until 12).map { i ->
            val x = (i % 4) * 4
            val z = (i / 4) * 5
            intArrayOf(x, 0, z, x + 1, 16, z + 1)
        }
        assertNull(BlockShapes.solidFromBoxes(posts, 0.5, 0.0, 1.0))
    }

    @Test fun `a shaped block gets a registered type per shape, shared between positions with the same shape`() {
        val world = mockk<BlockGetter>()
        val a = BlockPos(1, 2, 3)
        val b = BlockPos(4, 5, 6)
        val c = BlockPos(7, 8, 9)
        shapes[a] = plate(4)
        shapes[b] = plate(4)
        shapes[c] = plate(8)

        val typeA = BlockShapes.typeAt(world, a, panel)!!
        val typeB = BlockShapes.typeAt(world, b, panel)!!
        val typeC = BlockShapes.typeAt(world, c, panel)!!
        assertEquals(typeA, typeB)
        assertNotEquals(typeA, typeC)
        assertNotEquals(core.blockTypes.solid, typeA)
        assertEquals(listOf(listOf(0, 0, 0) to listOf(15, 3, 15)),
            boxesOf(typeA).map { it.first.toList() to it.second.toList() })
        assertEquals(listOf(listOf(0, 0, 0) to listOf(15, 7, 15)),
            boxesOf(typeC).map { it.first.toList() to it.second.toList() })

        // Nothing special to say, or not a shaped block at all: the block state's own type.
        assertNull(BlockShapes.typeAt(world, BlockPos(0, 0, 0), panel))
        assertNull(BlockShapes.typeAt(world, a, stone))
        assertNull(BlockShapes.typeAt(null, a, panel))
    }

    /** Stands in for the mass datapack: a panel's block state says it is a solid cube. */
    private fun stubBlockStateInfo() {
        mockkObject(BlockStateInfo)
        every { BlockStateInfo.get(stone) } returns (stoneMass to stoneType)
        every { BlockStateInfo.get(panel) } returns (panelMass to panelStateType)
        val cache = mockk<BlockStateInfo.Cache>()
        every { cache.get(any()) } answers { BlockStateInfo.get(firstArg<BlockState>()) }
        every { BlockStateInfo.cache } returns cache
    }

    @Test fun `placing a shaped block hands the engine its registered shape, not the block state's`() {
        stubBlockStateInfo()
        val registry = BlockStateInfo::class.java.getDeclaredField("SORTED_REGISTRY").apply { isAccessible = true }
        val saved = runCatching { registry.get(BlockStateInfo) }.getOrNull()
        registry.set(BlockStateInfo, emptyList<BlockStateInfoProvider>())
        try {
            val level = mockk<ClientLevel>(relaxed = true)
            val ships = mockk<VsiShipWorld>(relaxed = true)
            // The Level overload, which is the one BlockStateInfo asks; ClientLevel has its own.
            every { (level as Level).shipObjectWorld } returns ships
            every { level.dimensionId } returns "minecraft:dimension:minecraft:overworld"
            val pos = BlockPos(10, 64, 10)
            shapes[pos] = plate(4)

            BlockStateInfo.onSetBlock(level, pos, stone, panel)

            val newType = slot<VsiBlockType>()
            verify {
                ships.onSetBlock(10, 64, 10, "minecraft:dimension:minecraft:overworld",
                    stoneType, capture(newType), stoneMass, panelMass)
            }
            assertEquals(BlockShapes.typeAt(level, pos, panel), newType.captured)
            assertNotEquals(panelStateType, newType.captured)
        } finally {
            registry.set(BlockStateInfo, saved)
        }
    }

    @Test fun `a chunk baked for the engine carries each shaped block's registered type`() {
        stubBlockStateInfo()
        mockkObject(FractionalFluidSources.Companion)
        every { FractionalFluidSources.fluidName(any<BlockState>()) } returns null
        val level = mockk<ClientLevel>(relaxed = true)
        val added = HashMap<Triple<Int, Int, Int>, VsiBlockType>()
        val builder = mockk<VsiTerrainUpdate.Builder>(relaxed = true)
        every { builder.addBlock(any(), any(), any(), any()) } answers {
            added[Triple(firstArg(), secondArg(), thirdArg())] = arg(3)
        }
        val spied = spyk(core)
        every { spied.newDenseTerrainUpdateBuilder(any(), any(), any()) } returns builder
        every { builder.build() } returns mockk<VsiTerrainUpdate>()
        every { vsCore } returns spied

        val section = mockk<LevelChunkSection> {
            every { getBlockState(any(), any(), any()) } answers {
                if (firstArg<Int>() == 3 && secondArg<Int>() == 4 && thirdArg<Int>() == 5) panel else stone
            }
        }
        // Chunk (1, 4, 2), so the panel at local (3, 4, 5) is at world (19, 68, 37).
        shapes[BlockPos(19, 68, 37)] = plate(4)
        section.toDenseVoxelUpdate(Vector3i(1, 4, 2), level)

        assertEquals(4096, added.size)
        val expected = BlockShapes.typeAt(level, BlockPos(19, 68, 37), panel)
        assertEquals(expected, added[Triple(3, 4, 5)])
        assertNotEquals(panelStateType, added[Triple(3, 4, 5)])
        assertEquals(stoneType, added[Triple(0, 0, 0)])
    }

    @Test fun `a shape that changes under the same block state is sent as a fresh block of the new shape`() {
        stubBlockStateInfo()
        val level = mockk<ServerLevel>(relaxed = true)
        val ships = mockk<VsiServerShipWorld>(relaxed = true)
        every { level.shipObjectWorld } returns ships
        every { level.dimensionId } returns "minecraft:dimension:minecraft:overworld"
        val pos = BlockPos(2, 70, 2)
        every { level.getBlockState(pos) } returns panel

        shapes[pos] = plate(4)
        BlockShapes.onShapeChanged(level, pos)
        val dented = BlockShapes.typeAt(level, pos, panel)
        verify {
            ships.onSetBlock(2, 70, 2, "minecraft:dimension:minecraft:overworld",
                core.blockTypes.air, dented!!, panelMass, panelMass)
        }

        // Beaten back to nothing it has its own empty shape; a shape the block cannot describe
        // falls back to the block state's type, and the mass is never touched.
        shapes[pos] = null
        BlockShapes.onShapeChanged(level, pos)
        verify {
            ships.onSetBlock(2, 70, 2, "minecraft:dimension:minecraft:overworld",
                core.blockTypes.air, panelStateType, panelMass, panelMass)
        }

        val client = mockk<ClientLevel>(relaxed = true)
        val clientShips = mockk<VsiShipWorld>(relaxed = true)
        every { (client as Level).shipObjectWorld } returns clientShips
        BlockShapes.onShapeChanged(client, pos)
        verify(exactly = 0) { clientShips.onSetBlock(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Suppress("unused")
    private fun SolidState.boxes() = (shape as BoxesBlockShape).positiveBoxes
}
