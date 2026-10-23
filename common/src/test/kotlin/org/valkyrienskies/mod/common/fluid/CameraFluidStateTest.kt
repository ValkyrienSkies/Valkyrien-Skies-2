package org.valkyrienskies.mod.common.fluid

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.Camera
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.FogType
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.getShipMountedTo
import org.valkyrienskies.mod.mixin.feature.fluid_camera_fix.MixinCamera
import org.valkyrienskies.mod.mixin.client.MixinCamera as MountedCamera

class CameraFluidStateTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private val level = mockk<ClientLevel>()
    private val world = mockk<VsiClientShipWorld>(relaxed = true)
    private val ships = mutableListOf<ClientShip>()
    private val cameraPosition = Vec3(0.5, 0.25, 0.5)
    private val empty get() = Fluids.EMPTY.defaultFluidState()
    private var tick = 10L
    private var flood = floodingSnapshot(VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0))
    private var topology = topologySnapshot()
    private var oldGrace = 0
    private var oldFog = true

    @BeforeEach
    fun fixtures() {
        oldGrace = VSGameConfig.CLIENT.sealedAreaCameraGracePeriod
        oldFog = VSGameConfig.CLIENT.renderFloodedFluidFog
        VSGameConfig.CLIENT.sealedAreaCameraGracePeriod = 0
        VSGameConfig.CLIENT.renderFloodedFluidFog = true
        every { level.gameTime } answers { tick }
        every { level.getFluidState(any()) } answers { empty }
        every { world.loadedShips.iterator() } answers { ships.iterator() }
        every { world.getFluidGameplaySnapshot(any()) } answers {
            val id = firstArg<Long>()
            gameplaySnapshot(topology.copy(bodyId = id), flood.copy(bodyId = id))
        }
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
        every { MassDatapackResolver.getFlowingFluid(18) } returns Fluids.LAVA
    }

    @AfterEach
    fun cleanup() {
        VSGameConfig.CLIENT.sealedAreaCameraGracePeriod = oldGrace
        VSGameConfig.CLIENT.renderFloodedFluidFog = oldFog
        FloodedFluidClientCache.clear()
        FluidTopologyClientCache.clear()
        unmockkAll()
    }

    private fun ship(id: Long = 7, transform: Matrix4d = Matrix4d()): ClientShip {
        val ship = mockk<ClientShip>()
        every { ship.id } returns id
        every { ship.renderAABB } returns AABBd(-10.0, -10.0, -10.0, 10.0, 10.0, 10.0)
        every { ship.renderTransform.worldToShip } returns transform
        // Simulation transforms/broadphase are deliberately unstubbed: camera sampling must not read them.
        ships.add(ship)
        return ship
    }

    private fun sample(point: Vec3 = cameraPosition, vanilla: FluidState = empty, fog: Boolean = true) =
        CameraFluidState.sample(level, world, point, vanilla, fog)

    @Test
    fun `camera captures one complete pair even when a newer publication is ready on the next read`() {
        ship()
        val dry = gameplaySnapshot(topology.copy(streamSequence = 20), floodingSnapshot().copy(streamSequence = 20))
        val wet = gameplaySnapshot(topology.copy(streamSequence = 21), flood.copy(streamSequence = 21))
        var reads = 0
        every { world.getFluidGameplaySnapshot(7) } answers { if (reads++ == 0) dry else wet }
        every { world.getFluidTopologySnapshot(any()) } throws AssertionError("Independent topology read")
        every { world.getFluidFloodingSnapshot(any()) } throws AssertionError("Independent liquid read")
        val first = sample(vanilla = Fluids.WATER.defaultFluidState())
        assertTrue(first.dryDomain())
        assertTrue(first.fluid().isEmpty)
        assertEquals(1, reads)
        assertTrue(sample().submergedInShip())
        assertEquals(2, reads)
    }

    @Test
    fun `stationary camera observes interpolated ship movement inside one game tick`() {
        val transform = Matrix4d()
        ship(transform = transform)
        assertSame(Fluids.WATER, sample().fluid().type)
        transform.translation(0.0, 0.5, 0.0)
        assertTrue(sample(vanilla = Fluids.WATER.defaultFluidState()).dryDomain())
        assertTrue(sample().fluid().isEmpty)
        transform.translation(2.0, 0.0, 0.0)
        assertFalse(sample().dryDomain())
        assertSame(Fluids.LAVA.defaultFluidState(), sample(vanilla = Fluids.LAVA.defaultFluidState()).fluid())
    }

    @Test
    fun `new fill topology and fog configuration are visible without a new tick or camera position`() {
        ship()
        assertTrue(sample().submergedInShip())
        assertTrue(sample(fog = false).fluid().isEmpty)
        assertFalse(sample(fog = false).dryDomain())
        flood = flood.copy(streamSequence = 5, voxels = emptyList(), totalFloodedVoxelCount = 0)
        assertTrue(sample().dryDomain())
        topology = topology.copy(streamSequence = 4, voxels = emptyList(), totalDomainVoxelCount = 0)
        assertFalse(sample().dryDomain())
        flood = flood.copy(streamSequence = 6, totalFloodedVoxelCount = 1,
            voxels = listOf(VsiFluidFloodedVoxel(0, 0, 0, 18, 255, 0)))
        // Orphaned diagnostic components cannot supply authoritative retained immersion.
        every { world.getFluidTopologySnapshot(any()) } returns null
        every { world.getFluidFloodingSnapshot(any()) } answers { flood }
        every { world.getFluidGameplaySnapshot(any()) } returns null
        assertTrue(sample().fluid().isEmpty)
        verify(exactly = 0) { world.getFluidFloodingSnapshot(any()) }
    }

    @Test
    fun `distinct points in the same block resolve opposite sides of a rotated surface`() {
        ship(transform = Matrix4d().rotateZ(Math.PI / 2))
        flood = flood.copy(voxels = listOf(VsiFluidFloodedVoxel(-1, 0, 0, 18, 128, 0)))
        topology = topology.copy(voxels = listOf(org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel(-1, 0, 0, 1)))
        val center = Vec3(0.6, 0.25, 0.5)
        val plane = Vec3(0.4, 0.25, 0.5)
        assertEquals(BlockPos.containing(center), BlockPos.containing(plane))
        assertTrue(sample(center).dryDomain())
        assertSame(Fluids.LAVA, sample(plane).fluid().type)
        assertTrue(sample(plane).submergedInShip())
    }

    @Test
    fun `render bounds filter empty candidates and overlapping dry ships do not hide retained water`() {
        val distant = ship(8)
        every { distant.renderAABB } returns AABBd(100.0, 100.0, 100.0, 101.0, 101.0, 101.0)
        every { world.getFluidGameplaySnapshot(8) } throws AssertionError("Out-of-render-bounds ship was sampled")
        ship(9)
        every { world.getFluidGameplaySnapshot(9) } returns gameplaySnapshot(
            topology.copy(bodyId = 9), floodingSnapshot().copy(bodyId = 9))
        ship()
        assertTrue(sample().submergedInShip())
        ships.reverse()
        assertTrue(sample().submergedInShip())
    }

    @Test
    fun `ship block fallback uses local height and retains a hit across overlapping empty ships`() {
        ship(transform = Matrix4d().translation(100.0, 0.0, 0.0))
        ship(8, Matrix4d().translation(200.0, 0.0, 0.0))
        every { world.getFluidGameplaySnapshot(any()) } returns null
        val flowing = mockk<FluidState>()
        every { flowing.isEmpty } returns false
        every { flowing.getHeight(level, BlockPos(100, 0, 0)) } returns 0.3f
        every { level.getFluidState(BlockPos(100, 0, 0)) } returns flowing
        assertSame(flowing, sample().fluid())
        assertTrue(sample().submergedInShip())
        ships.reverse()
        assertSame(flowing, sample().fluid())
        assertTrue(sample(Vec3(0.5, 0.4, 0.5)).fluid().isEmpty)
        assertSame(Fluids.LAVA.defaultFluidState(), sample(vanilla = Fluids.LAVA.defaultFluidState()).fluid())
    }

    @Test
    fun `camera grace uses its own dry point and tick time rather than query count`() {
        ship()
        val grace = CameraFluidState.Grace()
        val playerEyes = sample()
        val camera = sample(Vec3(0.5, 0.8, 0.5))
        assertTrue(playerEyes.submergedInShip())
        assertTrue(camera.dryDomain())
        assertTrue(grace.suppress(level, camera.dryDomain(), 2))
        repeat(20) { assertTrue(grace.suppress(level, false, 2)) }
        tick++
        assertTrue(grace.suppress(level, false, 2))
        tick++
        assertFalse(grace.suppress(level, false, 2))
        assertTrue(sample().submergedInShip(), "Camera state must not replace the eye query")
        assertFalse(grace.suppress(level, true, 0))
        assertFalse(grace.suppress(level, false, -1))
    }

    @Test
    fun `grace resets on world changes backwards clocks and explicit camera reset`() {
        val grace = CameraFluidState.Grace()
        assertTrue(grace.suppress(level, true, 2))
        val second = mockk<ClientLevel>()
        every { second.gameTime } answers { tick }
        assertFalse(grace.suppress(second, false, 2))
        assertTrue(grace.suppress(second, true, 2))
        tick--
        assertFalse(grace.suppress(second, false, 2))
        assertTrue(grace.suppress(second, true, 2))
        grace.clear()
        assertFalse(grace.suppress(second, false, 2))
    }

    @Test
    fun `camera hooks preserve individual sample positions height decisions and clear query state`() {
        ship()
        mockkStatic(CameraFluidState::class)
        every { CameraFluidState.sample(level, any(), any(), any()) } answers {
            CameraFluidState.sample(level, world, secondArg(), thirdArg(), arg(3))
        }
        val camera = object : MixinCamera() {
            override fun getPosition() = Vec3(0.5, 0.8, 0.5)
        }
        val fluidOperation = Operation<FluidState> { empty }
        fun height() = invoke(camera, "vs\$fluidHeight", empty, level, BlockPos.ZERO, Operation<Float> { 0.25f }) as Float
        val query = Operation<FogType> {
            assertTrue((invoke(camera, "vs\$centerFluid", level, BlockPos.ZERO, fluidOperation) as FluidState).isEmpty)
            assertEquals(0.25f, height())
            val plane = invoke(camera, "vs\$nearPlaneFluid", level, BlockPos.ZERO, fluidOperation, cameraPosition) as FluidState
            assertSame(Fluids.WATER, plane.type)
            assertEquals(Float.POSITIVE_INFINITY, height())
            invoke(camera, "vs\$nearPlaneFluid", level, BlockPos(3, 0, 0), fluidOperation, Vec3(3.5, 0.25, 0.5))
            assertEquals(0.25f, height())
            FogType.WATER
        }
        assertEquals(FogType.WATER, invoke(camera, "vs\$cameraGrace", query))
        assertEquals(0.25f, height())
        VSGameConfig.CLIENT.sealedAreaCameraGracePeriod = 2
        assertEquals(FogType.NONE, invoke(camera, "vs\$cameraGrace", query))
        invoke(camera, "vs\$resetFluid", CallbackInfo("reset", false))
        assertEquals(FogType.LAVA, invoke(camera, "vs\$cameraGrace", Operation<FogType> { FogType.LAVA }))
        // A failed render query must not leave a strong level reference or stale height override.
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            invoke(camera, "vs\$cameraGrace", Operation<FogType> { throw IllegalStateException("render failure") })
        }
        val levelField = MixinCamera::class.java.getDeclaredField("vs\$sampleLevel").apply { isAccessible = true }
        assertNull(levelField.get(camera))
    }

    @Test
    fun `mounted eye interpolation uses partial ticks independently of world Z`() {
        val mountedShip = ship()
        val rotation = Quaterniond().rotateX(Math.PI / 3)
        every { mountedShip.renderTransform.rotation } returns rotation
        val entity = mockk<Entity>()
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { getShipMountedTo(entity) } returns mountedShip
        val camera = object : MountedCamera() {
            override fun getMaxZoom(startingDistance: Double) = startingDistance
            override fun move(distanceOffset: Double, verticalOffset: Double, horizontalOffset: Double) = Unit
            override fun setPosition(x: Double, y: Double, z: Double) = Unit
        }
        fun set(name: String, value: Any) = MountedCamera::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.set(camera, value)
        set("entity", entity)
        set("eyeHeightOld", 1.0f)
        set("eyeHeight", 2.0f)
        val hook = MountedCamera::class.java.declaredMethods.single { it.name == "setPosition" && it.parameterCount == 6 }
            .apply { isAccessible = true }
        val target = mockk<Camera>()
        for (z in listOf(-5000.0, 0.0, 10000.0)) {
            for (partialTick in listOf(0.0f, 0.25f, 1.0f)) {
                val interpolatedHeight = 1.0 + partialTick
                val expected = rotation.transform(Vector3d(0.0, interpolatedHeight, 0.0))
                    .add(10.0, 20.0, z)
                var actual: Vector3d? = null
                val original = Operation<Void> { args ->
                    assertSame(target, args[0])
                    actual = Vector3d(args[1] as Double, args[2] as Double, args[3] as Double)
                    null
                }
                hook.invoke(camera, target, 10.0, 20.0 + interpolatedHeight, z, original, partialTick)
                assertTrue(expected.distance(actual) < 1e-6, "partialTick=$partialTick, z=$z, actual=$actual")
            }
        }
    }

    private fun invoke(camera: MixinCamera, name: String, vararg args: Any): Any? =
        MixinCamera::class.java.declaredMethods.single { it.name == name }
            .apply { isAccessible = true }.invoke(camera, *args)
}
