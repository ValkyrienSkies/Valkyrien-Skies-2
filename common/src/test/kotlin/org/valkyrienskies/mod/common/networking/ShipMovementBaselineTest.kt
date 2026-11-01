package org.valkyrienskies.mod.common.networking

import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.network.protocol.Packet
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.mod.mixin.server.network.MixinServerGamePacketListenerImpl

class ShipMovementBaselineTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val listener = object : MixinServerGamePacketListenerImpl() {
        override fun send(packet: Packet<*>) {}
    }

    private fun set(name: String, value: Any) =
        MixinServerGamePacketListenerImpl::class.java.getDeclaredField(name).apply { isAccessible = true }.set(listener, value)

    private fun get(name: String): Double =
        MixinServerGamePacketListenerImpl::class.java.getDeclaredField(name).apply { isAccessible = true }.getDouble(listener)

    @Test fun `ship drag advances first and last player baselines by only the carried displacement`() {
        val player = mockk<ServerPlayer>()
        listener.player = player
        set("firstGoodX", 10.0)
        set("lastGoodX", 10.3)
        listener.`vs$onShipDrag`(player, Vector3d(2.0, 1.0, -3.0))
        assertEquals(12.0, get("firstGoodX"))
        assertEquals(12.3, get("lastGoodX"))
        assertEquals(1.0, get("lastGoodY"))
        assertEquals(-3.0, get("lastGoodZ"))
        assertEquals(0.3, get("lastGoodX") - get("firstGoodX"), 1e-10)
    }

    @Test fun `vehicle carry updates only its movement validation baselines`() {
        val vehicle = mockk<Entity>()
        listener.player = mockk()
        set("lastVehicle", vehicle)
        set("vehicleFirstGoodX", 10.0)
        set("vehicleLastGoodX", 10.3)
        listener.`vs$onShipDrag`(vehicle, Vector3d(2.0, 1.0, -3.0))
        assertEquals(12.0, get("vehicleFirstGoodX"))
        assertEquals(12.3, get("vehicleLastGoodX"))
        assertEquals(1.0, get("vehicleLastGoodY"))
        assertEquals(-3.0, get("vehicleLastGoodZ"))
        assertEquals(0.0, get("lastGoodX"))
    }
}
