package org.valkyrienskies.mod.client

import net.minecraft.client.player.LocalPlayer
import org.valkyrienskies.mod.common.networking.PacketPlayerShipPush
import org.valkyrienskies.mod.common.util.ShipPushing
import org.valkyrienskies.mod.common.vsCore
import java.lang.ref.WeakReference

object PlayerShipPushClient {
    private var player: WeakReference<LocalPlayer>? = null

    @JvmStatic
    fun start(current: LocalPlayer) {
        player = WeakReference(current)
    }

    @JvmStatic
    fun tick(current: LocalPlayer) {
        if (player?.get() !== current) {
            player = null
            return
        }
        val active = ShipPushing.eligible(current)
        // Send movement intent even when a collision prevents the player from moving.
        vsCore.simplePacketNetworking.sendToServer(PacketPlayerShipPush(active,
            if (active) (if (current.input.left) 1.0f else 0.0f) - (if (current.input.right) 1.0f else 0.0f) else 0.0f,
            if (active) (if (current.input.up) 1.0f else 0.0f) - (if (current.input.down) 1.0f else 0.0f) else 0.0f))
        if (!active) player = null
    }
}
