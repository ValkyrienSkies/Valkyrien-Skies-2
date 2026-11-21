package org.valkyrienskies.mod.common.util

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import org.valkyrienskies.mod.mixinducks.client.ShipWalkAnimation
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform

class EntityLerperTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val info = EntityDraggingInformation()
    private val entity = mockk<Entity>(relaxed = true, moreInterfaces = arrayOf(IEntityDraggingInformationProvider::class, ShipWalkAnimation::class))
    private val ship = mockk<ClientShip>()
    private var position = Vector3d()
    private var yaw = 0f
    private var pitch = 0f
    private var headYaw = 0f

    private fun transform(x: Double, rotation: Double): ShipTransform {
        val matrix = Matrix4d().translation(x, 0.0, 0.0).rotateY(rotation)
        return mockk {
            every { shipToWorld } returns matrix
            every { worldToShip } returns matrix.invert(Matrix4d())
            every { shipToWorldRotation } returns Quaterniond().rotateY(rotation)
        }
    }

    @BeforeEach fun setup() {
        info.applyNetworkShip(1L, 0)
        info.usesShipInterpolation = true
        every { (entity as IEntityDraggingInformationProvider).draggingInformation } returns info
        every { entity.isControlledByLocalInstance } returns false
        every { entity.x } answers { position.x }
        every { entity.y } answers { position.y }
        every { entity.z } answers { position.z }
        every { entity.setPos(any<Double>(), any<Double>(), any<Double>()) } answers {
            position.set(firstArg<Double>(), secondArg<Double>(), thirdArg<Double>())
            Unit
        }
        every { entity.yRot } answers { yaw }
        every { entity.yRot = any() } answers { yaw = firstArg() }
        every { entity.xRot } answers { pitch }
        every { entity.xRot = any() } answers { pitch = firstArg() }
        every { entity.yHeadRot } answers { headYaw }
        every { entity.yHeadRot = any() } answers { headYaw = firstArg() }
    }

    @AfterEach fun cleanup() = unmockkAll()

    @Test fun `nonliving entities follow a moving local target in three ticks`() {
        info.lerpPositionOnShip = Vector3d(3.0, 0.0, 0.0)
        info.lerpYawOnShip = Math.toRadians(45.0)
        info.lerpPitchOnShip = 30.0
        info.lerpHeadYawOnShip = Math.toRadians(60.0)
        info.lerpSteps = 3
        info.headLerpSteps = 3
        for (tick in 1..3) {
            every { ship.prevTickTransform } returns transform((tick - 1).toDouble(), (tick - 1) * 0.1)
            every { ship.transform } returns transform(tick.toDouble(), tick * 0.1)
            EntityLerper.lerpStep(info, ship, entity)
            val local = ship.transform.worldToShip.transformPosition(position, Vector3d())
            assertEquals(tick.toDouble(), local.x, 1e-8)
            assertEquals(0.0, local.z, 1e-8)
        }
        assertEquals(0, info.lerpSteps)
        assertEquals(30f, pitch)
        assertEquals(45.0, Math.toDegrees(EntityLerper.yawToShip(ship, yaw.toDouble())), 1e-5)
        assertEquals(60.0, Math.toDegrees(EntityLerper.yawToShip(ship, headYaw.toDouble())), 1e-5)
    }

    @Test fun `walking on a stationary ship advances animation once after interpolation`() {
        every { ship.prevTickTransform } returns transform(0.0, 0.0)
        every { ship.transform } returns transform(0.0, 0.0)
        info.lerpPositionOnShip = Vector3d(0.6, 0.0, 0.0)
        info.lerpSteps = 3
        info.pendingWalkAnimation = false
        EntityLerper.lerpStep(info, ship, entity)
        verify(exactly = 1) { (entity as ShipWalkAnimation).`vs$updateShipWalkAnimation`(0.2f) }
        assertNull(info.pendingWalkAnimation)
        EntityLerper.lerpStep(info, ship, entity)
        verify(exactly = 1) { (entity as ShipWalkAnimation).`vs$updateShipWalkAnimation`(any()) }
    }

    @Test fun `standing on a translating rotating ship does not animate walking`() {
        position.set(3.0, 0.0, 0.0)
        every { ship.prevTickTransform } returns transform(0.0, 0.0)
        every { ship.transform } returns transform(10.0, Math.PI / 2)
        info.pendingWalkAnimation = false
        EntityLerper.lerpStep(info, ship, entity)
        verify { (entity as ShipWalkAnimation).`vs$updateShipWalkAnimation`(0f) }
    }

    @Test fun `walking advances every interpolation tick independently of deck motion`() {
        // Cover translation and rotation separately as well as together. The entity walks
        // 0.2 blocks per tick relative to the deck in every case, then stops walking while
        // the deck continues moving. Ship motion must neither inflate nor cancel the stride.
        val deckMotions = listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to Math.PI / 2, 10.0 to Math.PI / 2)
        for ((index, motion) in deckMotions.withIndex()) {
            position.set(3.0, 0.0, 0.0)
            info.lerpPositionOnShip = Vector3d(3.6, 0.0, 0.0)
            info.lerpSteps = 3
            for (tick in 1..4) {
                every { ship.prevTickTransform } returns transform((tick - 1) * motion.first, (tick - 1) * motion.second)
                every { ship.transform } returns transform(tick * motion.first, tick * motion.second)
                info.pendingWalkAnimation = false
                EntityLerper.lerpStep(info, ship, entity)
                assertNull(info.pendingWalkAnimation)
            }
            verify(exactly = (index + 1) * 3) {
                (entity as ShipWalkAnimation).`vs$updateShipWalkAnimation`(0.2f)
            }
            verify(exactly = index + 1) { (entity as ShipWalkAnimation).`vs$updateShipWalkAnimation`(0f) }
        }
    }

    @Test fun `vertical displacement follows the vanilla animation request`() {
        every { ship.prevTickTransform } returns transform(0.0, 0.0)
        every { ship.transform } returns transform(0.0, 0.0)
        info.lerpPositionOnShip = Vector3d(0.0, 0.6, 0.0)
        info.lerpSteps = 3
        info.pendingWalkAnimation = false
        EntityLerper.lerpStep(info, ship, entity)
        verify { (entity as ShipWalkAnimation).`vs$updateShipWalkAnimation`(0f) }
        info.pendingWalkAnimation = true
        EntityLerper.lerpStep(info, ship, entity)
        verify { (entity as ShipWalkAnimation).`vs$updateShipWalkAnimation`(0.2f) }
    }

    @Test fun `changing ships clears a deferred animation request`() {
        info.pendingWalkAnimation = true
        info.lastShipStoodOn = null
        assertNull(info.pendingWalkAnimation)
    }

    @Test fun `old vanilla lerps are neutralized before the entity tick`() {
        position.set(4.0, 5.0, 6.0)
        yaw = 20f
        pitch = 30f
        info.pendingWalkAnimation = true
        EntityLerper.prepareTick(entity)
        assertNull(info.pendingWalkAnimation)
        verify { entity.lerpTo(4.0, 5.0, 6.0, 20f, 30f, 0, true) }
        info.lastShipStoodOn = null
        EntityLerper.prepareTick(entity)
        verify(exactly = 1) { entity.lerpTo(any(), any(), any(), any(), any(), any(), any()) }
    }
}
