package org.valkyrienskies.mod.common.util

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
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
    private val entity = mockk<Entity>(relaxed = true, moreInterfaces = arrayOf(IEntityDraggingInformationProvider::class))
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

    @Test fun `old vanilla lerps are neutralized before the entity tick`() {
        position.set(4.0, 5.0, 6.0)
        yaw = 20f
        pitch = 30f
        EntityLerper.prepareTick(entity)
        verify { entity.lerpTo(4.0, 5.0, 6.0, 20f, 30f, 0, true) }
        info.lastShipStoodOn = null
        EntityLerper.prepareTick(entity)
        verify(exactly = 1) { entity.lerpTo(any(), any(), any(), any(), any(), any(), any()) }
    }
}
