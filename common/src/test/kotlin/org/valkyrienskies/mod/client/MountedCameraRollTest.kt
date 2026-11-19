package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.minecraft.SharedConstants
import net.minecraft.client.Camera
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.getShipMountedTo
import org.valkyrienskies.mod.mixin.client.MixinCamera
import org.valkyrienskies.mod.mixin.client.renderer.MixinGameRenderer
import org.valkyrienskies.mod.mixinducks.client.player.LocalPlayerDuck

class MountedCameraRollTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private val entity = mockk<Entity>()
    private val ship = mockk<ClientShip>()
    private val shipRotation = Quaterniond()
    private val rotation = Quaternionf()
    private val up = Vector3f()
    private val left = Vector3f()
    private val forwards = Vector3f()
    private val camera = object : MixinCamera() {
        override fun getMaxZoom(startingDistance: Double) = startingDistance
        override fun move(distanceOffset: Double, verticalOffset: Double, horizontalOffset: Double) = Unit
        override fun setPosition(x: Double, y: Double, z: Double) = Unit
    }

    @BeforeEach
    fun setup() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { getShipMountedTo(entity) } returns ship
        every { ship.renderTransform.rotation } returns shipRotation
        for ((name, value) in mapOf(
            "entity" to entity, "rotation" to rotation, "up" to up, "left" to left, "forwards" to forwards
        )) {
            MixinCamera::class.java.getDeclaredField(name).apply { isAccessible = true }.set(camera, value)
        }
    }

    @AfterEach
    fun cleanup() = unmockkAll()

    @Test
    fun `inverted ship turns camera upside down without changing aim`() {
        shipRotation.rotateZ(Math.PI)
        applyRotation(35.0f, 20.0f)
        assertEquals(180.0f, kotlin.math.abs(camera.getZrot()), 1e-4f)
        assertTrue(up.y < 0.0f)
        assertAim(35.0f, 20.0f)
    }

    @Test
    fun `camera up follows ship up for both third person directions and mixed rotations`() {
        for (roll in listOf(-170.0, -90.0, 0.0, 45.0, 180.0)) {
            shipRotation.rotationYXZ(0.7, 0.4, Math.toRadians(roll))
            for ((yaw, pitch) in listOf(0.0f to 0.0f, 65.0f to 30.0f, 245.0f to -30.0f)) {
                applyRotation(yaw, pitch)
                assertAim(yaw, pitch)
                val expectedUp = shipRotation.transform(Vector3d(0.0, 1.0, 0.0))
                val direction = Vector3d(forwards)
                expectedUp.sub(direction.mul(expectedUp.dot(direction))).normalize()
                assertTrue(expectedUp.distance(Vector3d(up)) < 1e-5)
                assertTrue(kotlin.math.abs(up.dot(left)) < 1e-5)
            }
        }
    }

    @Test
    fun `dismount clears roll`() {
        shipRotation.rotateZ(Math.PI)
        applyRotation(0.0f, 0.0f)
        every { getShipMountedTo(entity) } returns null
        applyRotation(0.0f, 0.0f)
        assertEquals(0.0f, camera.getZrot())
        assertEquals(Vector3f(0.0f, 1.0f, 0.0f), up)
    }

    @Test
    fun `looking along ship up retains finite previous roll`() {
        shipRotation.rotateZ(Math.PI / 4)
        applyRotation(0.0f, 0.0f)
        val previousRoll = camera.getZrot()
        shipRotation.identity().rotateX(Math.PI / 2)
        applyRotation(0.0f, 0.0f)
        assertEquals(previousRoll, camera.getZrot())
        assertTrue(rotation.isFinite)
    }

    @Test
    fun `mounted local frame keeps camera up continuous through vertical views in both perspectives`() {
        val player = mockk<Entity>(moreInterfaces = arrayOf(LocalPlayerDuck::class))
        val state = MountedCameraRotation()
        val mount = Any()
        every { getShipMountedTo(player) } returns ship
        every { (player as LocalPlayerDuck).`vs$getMountedCameraRotation`() } returns state
        MixinCamera::class.java.getDeclaredField("entity").apply { isAccessible = true }.set(camera, player)
        state.update(mount, 1, Quaterniond(), 0.0f, 0.0f)
        for (angle in listOf(0.0, 89.0, 90.0, 91.0, 180.0, 269.0, 270.0, 271.0, 360.0)) {
            shipRotation.identity().rotateX(Math.toRadians(angle)).rotateZ(0.3)
            state.update(mount, 1, shipRotation, state.worldYaw, state.worldPitch)
            val expectedUp = state.rotation.transform(Vector3d(0.0, 1.0, 0.0))
            for (reverse in listOf(false, true)) {
                val yaw = state.worldYaw + if (reverse) 180.0f else 0.0f
                val pitch = state.worldPitch * if (reverse) -1.0f else 1.0f
                applyRotation(yaw, pitch)
                assertTrue(expectedUp.distance(Vector3d(up)) < 1e-5)
                assertAim(yaw, pitch)
            }
        }
    }

    @Test
    fun `rendered view agrees with rolled camera basis`() {
        shipRotation.rotationXYZ(0.6, 0.3, 2.1)
        val yaw = 55.0f
        val pitch = -25.0f
        applyRotation(yaw, pitch)
        val renderCamera = mockk<Camera>(moreInterfaces = arrayOf(IVSCamera::class))
        every { (renderCamera as IVSCamera).getZrot() } returns camera.getZrot()
        val renderer = object : MixinGameRenderer() {
            override fun getFov(camera: Camera, f: Float, bl: Boolean) = 70.0
            override fun getProjectionMatrix(d: Double) = Matrix4f()
        }
        MixinGameRenderer::class.java.getDeclaredField("mainCamera")
            .apply { isAccessible = true }.set(renderer, renderCamera)
        val pose = PoseStack()
        MixinGameRenderer::class.java.declaredMethods.single { it.name == "vs\$applyMountedCameraRoll" }
            .apply { isAccessible = true }
            .invoke(renderer, 0.5f, 0L, pose, CallbackInfo("renderLevel", false))
        pose.mulPose(Axis.XP.rotationDegrees(pitch))
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f))
        assertTrue(pose.last().normal().transform(Vector3f(up)).distance(Vector3f(0.0f, 1.0f, 0.0f)) < 1e-5)
        assertTrue(pose.last().normal().transform(Vector3f(forwards)).distance(Vector3f(0.0f, 0.0f, -1.0f)) < 1e-5)
    }

    private fun applyRotation(yaw: Float, pitch: Float) {
        rotation.rotationYXZ(Math.toRadians(-yaw.toDouble()).toFloat(), Math.toRadians(pitch.toDouble()).toFloat(), 0.0f)
        rotation.transform(up.set(0.0f, 1.0f, 0.0f))
        rotation.transform(left.set(1.0f, 0.0f, 0.0f))
        rotation.transform(forwards.set(0.0f, 0.0f, 1.0f))
        MixinCamera::class.java.declaredMethods.single { it.name == "vs\$applyMountedRoll" }
            .apply { isAccessible = true }.invoke(camera, yaw, pitch, CallbackInfo("setRotation", false))
    }

    private fun assertAim(yaw: Float, pitch: Float) {
        val expected = Quaternionf().rotationYXZ(
            Math.toRadians(-yaw.toDouble()).toFloat(), Math.toRadians(pitch.toDouble()).toFloat(), 0.0f
        ).transform(Vector3f(0.0f, 0.0f, 1.0f))
        assertTrue(expected.distance(forwards) < 1e-5)
    }
}
