package org.valkyrienskies.mod.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.getShipMountedTo
import org.valkyrienskies.mod.mixin.client.player.MixinLocalPlayer
import sun.misc.Unsafe

class MountedCameraInputTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private lateinit var player: TestPlayer
    private val ship = mockk<ClientShip>()
    private val vehicle = mockk<Entity>(relaxed = true)
    private val rotation = Quaterniond().rotateZ(Math.PI)

    @BeforeEach
    fun setup() {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        player = unsafe.allocateInstance(TestPlayer::class.java) as TestPlayer
        player.mount = vehicle
        MixinLocalPlayer::class.java.getDeclaredField("vs\$mountedCameraRotation")
            .apply { isAccessible = true }.set(player, MountedCameraRotation())
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { getShipMountedTo(player) } returns ship
        every { ship.id } returns 1L
        every { ship.renderTransform.rotation } returns rotation
        every { ship.transform.rotation } returns rotation
    }

    @AfterEach
    fun cleanup() = unmockkAll()

    @Test
    fun `actual player turn converts mouse movement to world angles and keeps raycasts aligned`() {
        player.turn(100.0, 80.0)
        assertEquals(-15.0f, player.yRot, 1e-4f)
        assertEquals(-12.0f, player.xRot, 1e-4f)
        assertEquals(player.yRot, player.yRotO)
        assertEquals(player.xRot, player.xRotO)
        val cameraForward = player.`vs$getMountedCameraRotation`().rotation.transform(Vector3d(0.0, 0.0, 1.0))
        for (partialTick in listOf(0.0f, 0.5f, 1.0f)) {
            val aim = player.getViewVector(partialTick)
            assertTrue(cameraForward.distance(Vector3d(aim.x, aim.y, aim.z)) < 1e-4)
        }
        verify(exactly = 1) { vehicle.onPassengerTurned(player) }
    }

    @Test
    fun `player follows ship motion without mouse input and dismount restores vanilla turn`() {
        rotation.identity()
        player.`vs$updateMountedCameraRotation`(false)
        rotation.rotateY(Math.PI / 3).rotateX(Math.PI / 6)
        player.`vs$updateMountedCameraRotation`(true)
        val expected = rotation.transform(Vector3d(0.0, 0.0, 1.0))
        val aim = player.getViewVector(0.5f)
        assertTrue(expected.distance(Vector3d(aim.x, aim.y, aim.z)) < 1e-4)
        val yaw = player.yRot
        val pitch = player.xRot
        player.mount = null
        player.turn(100.0, 80.0)
        assertEquals(yaw + 15.0f, player.yRot, 1e-4f)
        assertEquals(pitch + 12.0f, player.xRot, 1e-4f)
        assertTrue(!player.`vs$getMountedCameraRotation`().isMounted)
    }

    class TestPlayer : MixinLocalPlayer() {
        var mount: Entity? = null
        override fun getVehicle() = mount
        override fun getArmorSlots(): Iterable<ItemStack> = emptyList()
        override fun getItemBySlot(slot: EquipmentSlot) = ItemStack.EMPTY
        override fun setItemSlot(slot: EquipmentSlot, stack: ItemStack) = Unit
        override fun getMainArm() = HumanoidArm.RIGHT
    }
}
