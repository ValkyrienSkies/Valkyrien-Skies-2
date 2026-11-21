package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import java.time.Duration
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.Level
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.internal.VsiCoreFactory
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent
import org.valkyrienskies.core.internal.physics.VsiFluidCurrent
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.config.MassDatapackResolver

class ShipFluidVolumeTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val world = mockk<VsiClientShipWorld>()
    private val ship = mockk<Ship>()
    private var transform = Matrix4d()
    private var topology = topologySnapshot()
    private var flooding = floodingSnapshot(VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0))
    private val water = mockk<FlowingFluid>()
    private val waterState = mockk<FluidState>()

    @BeforeEach fun setup() {
        every { ship.id } returns 7
        every { ship.shipToWorld } answers { transform }
        every { ship.worldToShip } answers { transform.invert(Matrix4d()) }
        every { world.getFluidGameplaySnapshot(7) } answers { gameplaySnapshot(topology, flooding) }
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17, true) } returns water
        every { water.defaultFluidState() } returns waterState
        every { waterState.`is`(FluidTags.WATER) } returns true
        every { waterState.`is`(FluidTags.LAVA) } returns false
    }
    @AfterEach fun cleanup() { ShipFluidInteraction.clear(world); unmockkAll() }
    private fun query(bounds: AABB) = ShipFluidInteraction.prepareVolume(world, listOf(ship), bounds)

    private fun flowing(current: VsiFluidCurrent?, top: Double = 1.0) {
        val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
        flooding = floodingSnapshot(VsiFluidFloodedVoxel(0, 0, 0, 17, (255 * top).toInt(), 0,
            surfaceRegions = listOf(VsiFluidSurfaceRegion(bounds, 0.0, 1.0, 0.0, 0.0, top,
                listOf(VsiFluidSurfaceConstituent(17, 255 * top)), current))))
        ShipFluidInteraction.clear(world)
    }

    @Test fun `retained current uses submerged volume and replaces its share of vanilla flow`() {
        flowing(VsiFluidCurrent(1.0, 0.0, 0.0, 0.0, 0.0, 0.0), .5)
        val q = query(AABB(.1, 0.0, .1, .9, 1.0, .9))
        val pushed = q.currentMovement(Vec3.ZERO, Vec3(0.0, 0.0, .014), { true }, { .014 })
        assertEquals(.007, pushed.x, 1e-12)
        assertEquals(.007, pushed.z, 1e-12)
        val dry = query(AABB(.1, .5, .1, .9, 1.5, .9))
        assertEquals(Vec3.ZERO, dry.currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { .014 }))
        val tiny = query(AABB(.1, .499999, .1, .9, 1.499999, .9))
        assertEquals(.014e-6, tiny.currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { .014 }).x, 1e-12)
    }

    @Test fun `retained currents rotate and scale once without adding ship translation`() {
        flowing(VsiFluidCurrent(.1, 0.0, 0.0, 0.0, 0.0, 0.0))
        transform.translation(100.0, 20.0, -30.0).rotateY(Math.PI / 2).scale(2.0)
        val q = query(AABB(100.1, 20.1, -31.9, 101.9, 21.9, -30.1))
        val push = q.currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { .014 })
        assertEquals(0.0, push.x, 1e-12)
        assertEquals(-.0028, push.z, 1e-12)
        flowing(VsiFluidCurrent(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertEquals(Vec3.ZERO, query(AABB(100.1, 20.1, -31.9, 101.9, 21.9, -30.1))
            .currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { .014 }))
    }

    @Test fun `angular current integrates at the submerged centroid and never accelerates beyond flow`() {
        flowing(VsiFluidCurrent(0.0, 0.0, 0.0, 0.0, 1.0, 0.0))
        val q = query(AABB(.5, .1, .1, 1.0, .9, .9))
        val push = q.currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { .014 })
        assertEquals(-.0035, push.z, 1e-12)
        assertEquals(0.0, push.x, 1e-12)
        val fast = Vec3(0.0, 0.0, -1.0)
        assertEquals(fast, q.currentMovement(fast, fast, { true }, { .014 }))
        assertEquals(Vec3.ZERO, q.currentMovement(Vec3.ZERO, Vec3.ZERO, { false }, { .014 }))
        assertEquals(Vec3.ZERO, q.currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { 0.0 }))
    }

    @Test fun `legacy current absence preserves original fluid motion`() {
        flowing(null)
        val movement = Vec3(.01, -.02, .03)
        assertEquals(movement, query(AABB(.1, .1, .1, .9, .9, .9))
            .currentMovement(Vec3.ZERO, movement, { true }, { .014 }))
    }

    @Test fun `current-only publication invalidates gameplay cache without changing a captured query`() {
        flowing(VsiFluidCurrent(.2, 0.0, 0.0, 0.0, 0.0, 0.0))
        val before = query(AABB(.1, .1, .1, .9, .9, .9))
        val surface = flooding.voxels.single().surfaceRegions.single()
        flooding = flooding.copy(streamSequence = flooding.streamSequence + 1,
            nativePublicationId = flooding.nativePublicationId + 1,
            voxels = listOf(flooding.voxels.single().copy(surfaceRegions = listOf(surface.copy(
                current = VsiFluidCurrent(-.2, 0.0, 0.0, 0.0, 0.0, 0.0))))))
        val after = query(AABB(.1, .1, .1, .9, .9, .9))
        assertEquals(.0028, before.currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { .014 }).x, 1e-12)
        assertEquals(-.0028, after.currentMovement(Vec3.ZERO, Vec3.ZERO, { true }, { .014 }).x, 1e-12)
    }

    @Test fun `entity straddling a dry domain keeps exterior world immersion`() {
        flooding = floodingSnapshot()
        val q = query(AABB(0.2, 0.1, 0.2, 1.2, 0.9, 0.8))
        assertTrue(q.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
        assertEquals(1.0f, q.worldHeight(BlockPos(1, 0, 0), 1.0f))
        assertEquals(0.0, q.retainedDepth(FluidTags.WATER))
    }

    @Test fun `entity queries capture one pair and ignore orphaned diagnostic components`() {
        every { world.getFluidTopologySnapshot(any()) } throws AssertionError("Independent topology read")
        every { world.getFluidFloodingSnapshot(any()) } throws AssertionError("Independent liquid read")
        assertTrue(query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)).retainedDepth(FluidTags.WATER) > 0.0)
        verify(exactly = 1) { world.getFluidGameplaySnapshot(7) }
        every { world.getFluidGameplaySnapshot(7) } returns null
        val missing = query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9))
        assertFalse(missing.hasDomains())
        assertEquals(0.0, missing.retainedDepth(FluidTags.WATER))
        assertEquals(1.0f, missing.worldHeight(BlockPos.ZERO, 1.0f))
    }

    @Test fun `partial cell domain masks only its portion of a world block`() {
        flooding = floodingSnapshot()
        transform.scale(0.5)
        val q = query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9))
        assertEquals(1.0f, q.worldHeight(BlockPos.ZERO, 1.0f), 1e-6f)
        val enclosed = query(AABB(0.1, 0.1, 0.1, 0.4, 0.4, 0.4))
        assertTrue(enclosed.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
    }

    @Test fun `retained depth and world masking use distinct ownership and preserve fluid types`() {
        val q = query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9))
        assertEquals(128.0 / 255 - 0.1, q.retainedDepth(FluidTags.WATER), 1e-12)
        assertEquals(0.0, q.retainedDepth(FluidTags.LAVA))
        assertTrue(q.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
        val found = mutableMapOf<FluidState, Double>()
        q.forEachRetained { state, depth -> found[state] = depth }
        assertEquals(mapOf(waterState to (128.0 / 255 - 0.1)), found)
        assertTrue(q.controlsCell(7, BlockPos.ZERO))
        assertFalse(q.controlsCell(7, BlockPos(1, 0, 0)))
    }

    @Test fun `tilted cell rejects enclosing box corners`() {
        transform.rotateZ(Math.PI / 4)
        val q = query(AABB(0.6, 0.1, 0.2, 0.65, 0.15, 0.8))
        assertEquals(0.0, q.retainedDepth(FluidTags.WATER))
        assertEquals(1.0f, q.worldHeight(BlockPos.ZERO, 1.0f), 1e-6f)
    }

    @Test fun `tiny scale and huge local coordinates do not scan empty inverse transformed volume`() {
        transform.scale(1e-9)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val q = query(AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0))
            assertTrue(q.retainedDepth(FluidTags.WATER) > 1.0)
            assertTrue(q.hasDomains())
        }
        topology = topology.copy(streamSequence = 4, voxels = listOf(VsiFluidTopologyVoxel(Int.MAX_VALUE, 0, 0, 1)))
        flooding = flooding.copy(streamSequence = 5, voxels = listOf(VsiFluidFloodedVoxel(Int.MAX_VALUE, 0, 0, 17, 255, 0)))
        transform.identity().translation(-Int.MAX_VALUE.toDouble(), 0.0, 0.0)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            assertEquals(0.8, query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)).retainedDepth(FluidTags.WATER), 1e-12)
        }
    }

    @Test fun `cached runs coalesce a complete domain without filling its holes`() {
        flooding = floodingSnapshot()
        val cells = (0..3).flatMap { x -> (0..3).flatMap { y -> (0..3).map { z -> VsiFluidTopologyVoxel(x, y, z, 1) } } }
        topology = topology.copy(voxels = cells, totalDomainVoxelCount = cells.size)
        val body = ShipFluidInteraction.bodyFor(world, 7)!!
        val field = body.javaClass.getDeclaredField("domainBoxes").apply { isAccessible = true }
        assertEquals(1, (field.get(body) as List<*>).size)
        flooding = flooding.copy(streamSequence = 5)
        assertSame(field.get(body), field.get(ShipFluidInteraction.bodyFor(world, 7)), "Fill updates must reuse domain runs")
        assertTrue(query(AABB(0.1, 0.1, 0.1, 3.9, 3.9, 3.9)).worldHeight(BlockPos(1, 1, 1), 1.0f).isNaN())
        topology = topology.copy(streamSequence = 4, totalDomainVoxelCount = cells.size - 1,
            voxels = cells.filterNot { it.positionX == 1 && it.positionY == 1 && it.positionZ == 1 })
        assertEquals(1.0f, query(AABB(0.1, 0.1, 0.1, 3.9, 3.9, 3.9)).worldHeight(BlockPos(1, 1, 1), 1.0f))
    }

    @Test fun `stationary player in a rotated full block room stays dry at shipyard coordinates`() {
        flooding = floodingSnapshot()
        val localX = -28_661_760
        val localY = 128
        val localZ = 12_290_048
        val cells = (-3..2).flatMap { x -> (-3..2).flatMap { y -> (-3..2).map { z ->
            VsiFluidTopologyVoxel(localX + x, localY + y, localZ + z, 1)
        } } }
        topology = topology.copy(voxels = cells, totalDomainVoxelCount = cells.size)
        val random = Random(7192026)
        repeat(100) { orientation ->
            transform.identity().translation(100.5, 65.0, -300.5)
                .rotateXYZ(random.nextDouble(-0.3, 0.3), random.nextDouble(-Math.PI, Math.PI),
                    random.nextDouble(-0.3, 0.3))
                .translate(-localX.toDouble(), -localY.toDouble(), -localZ.toDouble())
            val q = query(AABB(100.2, 64.1, -300.8, 100.8, 65.9, -300.2))
            assertTrue(q.hasDomains())
            assertEquals(0.0, q.retainedDepth(FluidTags.WATER))
            for (y in 64..65) {
                assertTrue(q.worldHeight(BlockPos(100, y, -301), 1.0f).isNaN(),
                    "Dry room reported immersion at orientation $orientation, y=$y")
            }
        }
    }

    @Test fun `placing a torch preserves dry immersion throughout a tilted sealed room`() {
        flooding = floodingSnapshot()
        val localX = -28_661_760
        val localY = 128
        val localZ = 12_290_048
        val torch = Blocks.TORCH.defaultBlockState()
        assertTrue(torch.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty)
        val voids = Shapes.join(Shapes.block(), torch.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
            BooleanOp.ONLY_FIRST).toAabbs().map { box ->
            ((box.minX * 16).toLong() or ((box.minY * 16).toLong() shl 5) or
                ((box.minZ * 16).toLong() shl 10) or ((box.maxX * 16).toLong() shl 15) or
                ((box.maxY * 16).toLong() shl 20) or ((box.maxZ * 16).toLong() shl 25) or (1L shl 30))
        }
        val cells = (-3..2).flatMap { x -> (-3..2).flatMap { y -> (-3..2).map { z ->
            VsiFluidTopologyVoxel(localX + x, localY + y, localZ + z, 1,
                if (x == -2 && y == -3 && z == -2) voids else emptyList())
        } } }
        for (placed in listOf(false, true, false)) {
            topology = topology.copy(streamSequence = topology.streamSequence + 1,
                voxels = if (placed) cells else cells.map { it.copy(regions = emptyList()) }, totalDomainVoxelCount = cells.size)
            val random = Random(11032026)
            repeat(120) { orientation ->
                transform.identity().translation(100.5, 65.0, -300.5)
                    .rotateXYZ(random.nextDouble(-0.3, 0.3), random.nextDouble(-Math.PI, Math.PI),
                        random.nextDouble(-0.3, 0.3))
                    .translate(-localX.toDouble(), -localY.toDouble(), -localZ.toDouble())
                for (x in -1..1) for (z in -1..1) {
                    val center = transform.transformPosition(Vector3d(localX + x.toDouble(), localY.toDouble(), localZ + z.toDouble()))
                    val bounds = AABB(center.x - .3, center.y - .9, center.z - .3,
                        center.x + .3, center.y + .9, center.z + .3)
                    val q = query(bounds)
                    assertEquals(0.0, q.retainedDepth(FluidTags.WATER))
                    for (pos in BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
                        assertTrue(q.worldHeight(pos, 1.0f).isNaN(),
                            "Torch room leaked away from torch: placed=$placed orientation=$orientation x=$x z=$z block=$pos")
                    }
                }
            }
        }
    }

    @Test fun `standing and wall torch outlines do not reintroduce exterior water`() {
        flooding = floodingSnapshot()
        val level = mockk<Level>()
        every { level.getBlockCollisions(null, any()) } returns emptyList()
        for (block in listOf(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH)) {
            val state = block.defaultBlockState()
            assertTrue(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty)
            every { level.getBlockState(any()) } answers {
                if (firstArg<BlockPos>() == BlockPos.ZERO) state else Blocks.AIR.defaultBlockState()
            }
            val outline = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
            val voids = Shapes.join(Shapes.block(), outline, BooleanOp.ONLY_FIRST).toAabbs().map { box ->
                ((box.minX * 16).toLong() or ((box.minY * 16).toLong() shl 5) or
                    ((box.minZ * 16).toLong() shl 10) or ((box.maxX * 16).toLong() shl 15) or
                    ((box.maxY * 16).toLong() shl 20) or ((box.maxZ * 16).toLong() shl 25) or (1L shl 30))
            }
            topology = topology.copy(streamSequence = topology.streamSequence + 1,
                totalDomainVoxelCount = 1, voxels = listOf(VsiFluidTopologyVoxel(0, 0, 0, 1, voids)))
            transform.identity()
            val bounds = AABB(.02, .02, .02, .98, .98, .98)
            val q = ShipFluidInteraction.prepareVolume(level, world, listOf(ship), bounds)
            assertTrue(q.worldHeight(BlockPos.ZERO, 1.0f).isNaN(), "Exterior water at $state")
            assertEquals(0.0, q.retainedDepth(FluidTags.WATER))
            val center = outline.bounds().center
            // Exercise the lazy hull path when the query intersects no empty-domain boxes at all.
            val inside = AABB(center.x - .01, center.y - .01, center.z - .01,
                center.x + .01, center.y + .01, center.z + .01)
            assertTrue(ShipFluidInteraction.prepareVolume(level, world, listOf(ship), inside)
                .worldHeight(BlockPos.ZERO, 1.0f).isNaN())
            val random = Random(2017)
            repeat(30) { orientation ->
                transform.identity().translation(100.5, 64.5, -300.5)
                    .rotateXYZ(random.nextDouble(), random.nextDouble() * 6, random.nextDouble())
                    .scale(.4 + random.nextDouble() * 2)
                val moved = transform.transformPosition(Vector3d(center.x, center.y, center.z))
                val rotated = AABB(moved.x - .001, moved.y - .001, moved.z - .001,
                    moved.x + .001, moved.y + .001, moved.z + .001)
                val rotatedQuery = ShipFluidInteraction.prepareVolume(level, world, listOf(ship), rotated)
                for (pos in BlockPos.betweenClosed(BlockPos.containing(rotated.minX, rotated.minY, rotated.minZ),
                    BlockPos.containing(rotated.maxX, rotated.maxY, rotated.maxZ))) {
                    assertTrue(rotatedQuery.worldHeight(pos, 1.0f).isNaN(), "$state orientation $orientation")
                }
            }
            transform.identity()
            val straddling = ShipFluidInteraction.prepareVolume(level, world, listOf(ship),
                AABB(.8, .2, .2, 1.2, .8, .8))
            assertEquals(1.0f, straddling.worldHeight(BlockPos(1, 0, 0), 1.0f))
            topology = topology.copy(streamSequence = topology.streamSequence + 1,
                totalDomainVoxelCount = 0, voxels = emptyList())
            assertEquals(1.0f, ShipFluidInteraction.prepareVolume(level, world, listOf(ship), bounds)
                .worldHeight(BlockPos.ZERO, 1.0f), "An exterior torch must not create an air pocket")
        }
    }

    @Test fun `partial outline masks preserve unowned voids and retained fluid`() {
        val level = mockk<Level>()
        every { level.getBlockCollisions(null, any()) } returns emptyList()
        every { level.getBlockState(any()) } returns Blocks.TORCH.defaultBlockState()
        // Only the left part of the cell is a published dry domain. Do not fill its other empty space.
        val left = (7L shl 15) or (16L shl 20) or (16L shl 25) or (1L shl 30)
        topology = topology.copy(voxels = listOf(VsiFluidTopologyVoxel(0, 0, 0, 1, listOf(left))))
        flooding = floodingSnapshot()
        val outside = ShipFluidInteraction.prepareVolume(level, world, listOf(ship),
            AABB(.7, .2, .7, .9, .8, .9))
        assertEquals(1.0f, outside.worldHeight(BlockPos.ZERO, 1.0f))
        flooding = floodingSnapshot(VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0))
            .copy(streamSequence = flooding.streamSequence + 1)
        val wet = ShipFluidInteraction.prepareVolume(level, world, listOf(ship),
            AABB(.1, .1, .1, .9, .9, .9))
        wet.worldHeight(BlockPos.ZERO, 1.0f) // Force lazy hull masks before checking retained contact.
        assertEquals(128.0 / 255 - .1, wet.retainedDepth(FluidTags.WATER), 1e-12)
    }

    @Test fun `small ship outline masks use sparse topology beyond the collision scan cap`() {
        flooding = floodingSnapshot()
        val level = mockk<Level>()
        val torch = Blocks.TORCH.defaultBlockState()
        every { level.getBlockState(any()) } answers {
            if (firstArg<BlockPos>() == BlockPos.ZERO) torch else Blocks.AIR.defaultBlockState()
        }
        // The collision query is intentionally not mocked: this case must not scan the large local volume.
        val voids = Shapes.join(Shapes.block(), torch.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
            BooleanOp.ONLY_FIRST).toAabbs().map { box ->
            ((box.minX * 16).toLong() or ((box.minY * 16).toLong() shl 5) or
                ((box.minZ * 16).toLong() shl 10) or ((box.maxX * 16).toLong() shl 15) or
                ((box.maxY * 16).toLong() shl 20) or ((box.maxZ * 16).toLong() shl 25) or (1L shl 30))
        }
        val cells = (0 until 12).flatMap { x -> (0 until 36).flatMap { y -> (0 until 12).map { z ->
            VsiFluidTopologyVoxel(x, y, z, 1, if (x == 0 && y == 0 && z == 0) voids else emptyList())
        } } }
        topology = topology.copy(voxels = cells, totalDomainVoxelCount = cells.size)
        transform.scaling(.05)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val q = ShipFluidInteraction.prepareVolume(level, world, listOf(ship),
                AABB(.001, .001, .001, .599, 1.799, .599))
            assertTrue(q.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
            assertTrue(q.worldHeight(BlockPos(0, 1, 0), 1.0f).isNaN())
        }
        verify(exactly = 0) { level.getBlockCollisions(any(), any()) }
    }

    @Test fun `player colliding with a tilted sealed floor and wall stays dry`() {
        flooding = floodingSnapshot()
        val localX = -28_661_760
        val localY = 128
        val localZ = 12_290_048
        val cells = (-3..2).flatMap { x -> (1..4).flatMap { y -> (-3..2).map { z ->
            VsiFluidTopologyVoxel(localX + x, localY + y, localZ + z, 1)
        } } }
        topology = topology.copy(voxels = cells, totalDomainVoxelCount = cells.size)
        val collider = VsiCoreFactory.instance.newVsCoreServer(mockk(relaxed = true)).entityPolygonCollider
        val level = mockk<Level>()
        for (tilt in listOf(-0.6, -0.2, 0.0, 0.2, 0.6)) for (yaw in listOf(0.0, 0.7, 2.2)) {
            transform.identity().translation(100.5, 64.0, -300.5).rotateY(yaw).rotateZ(tilt)
                .translate(-localX.toDouble(), -localY.toDouble(), -localZ.toDouble())
            val floor = AABBd(localX - 4.0, localY.toDouble(), localZ - 4.0,
                localX + 4.0, localY + 1.0, localZ + 4.0)
            val wall = AABBd(localX + 3.0, localY + 1.0, localZ - 4.0,
                localX + 4.0, localY + 5.0, localZ + 4.0)
            val polygons = listOf(floor, wall).map { collider.createPolygonFromAABB(it, transform, 7) }
            every { level.getBlockCollisions(null, any()) } returns listOf(floor, wall).map {
                Shapes.create(AABB(it.minX, it.minY, it.minZ, it.maxX, it.maxY, it.maxZ))
            }
            val start = transform.transformPosition(Vector3d(localX.toDouble(), localY + 2.0, localZ.toDouble()))
            var bounds = AABB(start.x - 0.3, start.y, start.z - 0.3, start.x + 0.3, start.y + 1.8, start.z + 0.3)
            repeat(80) { tick ->
                val horizontal = transform.transformDirection(Vector3d(if (tick > 30) 0.08 else 0.0, 0.0, 0.0))
                val movement = Vector3d(horizontal.x, -0.08, horizontal.z)
                val response = collider.adjustEntityMovementForPolygonCollisions(movement,
                    AABBd(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ),
                    0.6, polygons, true).first
                bounds = bounds.move(response.x(), response.y(), response.z())
                val q = ShipFluidInteraction.prepareVolume(level, world, listOf(ship), bounds.deflate(0.001))
                for (pos in BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
                    assertTrue(q.worldHeight(pos, 1.0f).isNaN(),
                        "Wet contact: tilt=$tilt yaw=$yaw tick=$tick bounds=$bounds block=$pos")
                }
            }
        }
    }

    @Test fun `ordinary small queries use bounded hash probes and huge queries enumerate sparse cells`() {
        val cells = (0 until 5000).map { VsiFluidFloodedVoxel(it, 0, 0, 17, 255, 0) }
        val body = spyk(ShipFluidInteraction.CachedBody(topology, flooding.copy(voxels = cells)))
        val found = mutableListOf<VsiFluidFloodedVoxel>()
        body.forEachFlooded(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)) { found.add(it) }
        assertEquals(listOf(cells.first()), found)
        verify(exactly = 1) { body.getFloodedVoxel(any(), any(), any()) }
        clearMocks(body, answers = false)
        found.clear()
        body.forEachFlooded(AABB(-1e20, -1e20, -1e20, 1e20, 1e20, 1e20)) { found.add(it) }
        assertEquals(5000, found.size)
        verify(exactly = 0) { body.getFloodedVoxel(any(), any(), any()) }
    }

    @Test fun `solid hull masks are lazy and preserve real water beyond the wall`() {
        flooding = floodingSnapshot()
        val level = mockk<Level>()
        every { level.getBlockCollisions(null, any()) } returns listOf(Shapes.box(1.0, 0.0, 0.0, 2.0, 1.0, 1.0))
        val q = ShipFluidInteraction.prepareVolume(level, world, listOf(ship), AABB(0.2, 0.1, 0.2, 2.2, 0.9, 0.8))
        assertTrue(q.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
        verify(exactly = 0) { level.getBlockCollisions(any(), any()) }
        assertTrue(q.worldHeight(BlockPos(1, 0, 0), 1.0f).isNaN())
        assertEquals(1.0f, q.worldHeight(BlockPos(2, 0, 0), 1.0f))
        verify(exactly = 1) { level.getBlockCollisions(null, any()) }
    }

    @Test fun `partial hull shapes leave water in their empty portion and retain interior liquid`() {
        val level = mockk<Level>()
        every { level.getBlockCollisions(null, any()) } returns listOf(Shapes.box(1.0, 0.0, 0.0, 2.0, 0.5, 1.0))
        val q = ShipFluidInteraction.prepareVolume(level, world, listOf(ship), AABB(0.2, 0.1, 0.2, 1.2, 0.9, 0.8))
        assertEquals(1.0f, q.worldHeight(BlockPos(1, 0, 0), 1.0f))
        assertEquals(128.0 / 255 - 0.1, q.retainedDepth(FluidTags.WATER), 1e-12)
        val below = ShipFluidInteraction.prepareVolume(level, world, listOf(ship), AABB(0.2, 0.1, 0.2, 1.2, 0.4, 0.8))
        assertTrue(below.worldHeight(BlockPos(1, 0, 0), 1.0f).isNaN())
    }

    @Test fun `tiny ships do not cause unbounded hull block scans`() {
        val level = mockk<Level>()
        transform.scale(1e-9)
        val q = ShipFluidInteraction.prepareVolume(level, world, listOf(ship), AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0))
        assertEquals(1.0f, q.worldHeight(BlockPos.ZERO, 1.0f))
        verify(exactly = 0) { level.getBlockCollisions(any(), any()) }
    }
}
