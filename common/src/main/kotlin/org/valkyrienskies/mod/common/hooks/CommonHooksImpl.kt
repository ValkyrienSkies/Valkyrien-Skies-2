package org.valkyrienskies.mod.common.hooks

import net.minecraft.client.Minecraft
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksOut
import org.valkyrienskies.core.internal.hooks.VsiPlayState
import org.valkyrienskies.core.internal.hooks.VsiPlayState.CLIENT_MULTIPLAYER
import org.valkyrienskies.core.internal.hooks.VsiPlayState.CLIENT_SINGLEPLAYER
import org.valkyrienskies.core.internal.hooks.VsiPlayState.CLIENT_TITLESCREEN
import org.valkyrienskies.core.internal.hooks.VsiPlayState.SERVERSIDE
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore

abstract class CommonHooksImpl : VsiCoreHooksOut {

    override var enableBlockEdgeConnectivity: Boolean
        get() = vsCore.hooks.enableBlockEdgeConnectivity
        set(value) {}

    override var enableBlockCornerConnectivity: Boolean
        get() = vsCore.hooks.enableBlockCornerConnectivity
        set(value) {}

    override var enableConnectivity: Boolean
        get() = vsCore.hooks.enableConnectivity
        set(value) {}

    override var enableWorldConnectivity: Boolean
        get() = vsCore.hooks.enableWorldConnectivity
        set(value) {}

    override var enableSplitting: Boolean
        get() = vsCore.hooks.enableSplitting
        set(value) {}

    override var enableDrag: Boolean
        get() = vsCore.hooks.enableDrag
        set(value) {}

    override var enableLift: Boolean
        get() = vsCore.hooks.enableLift
        set(value) {}

    override var dragCoefficient: Double
        get() = vsCore.hooks.dragCoefficient
        set(value) {}

    override var dragMultiplier: Double
        get() = vsCore.hooks.dragMultiplier
        set(value) {}

    override var liftMultiplier: Double
        get() = vsCore.hooks.liftMultiplier
        set(value) {}

    override val playState: VsiPlayState
        get() {
            if (!isPhysicalClient) {
                return SERVERSIDE
            }

            // Client is not connected to any game
            if (Minecraft.getInstance().connection?.connection?.isConnected != true) {
                return CLIENT_TITLESCREEN
            }

            // Client is in Singleplayer (or has their singleplayer world open to LAN)
            if (Minecraft.getInstance().singleplayerServer != null) {
                return CLIENT_SINGLEPLAYER
            }

            return CLIENT_MULTIPLAYER
        }

    override val currentShipServerWorld: ShipWorld?
        get() = ValkyrienSkiesMod.currentServer?.shipObjectWorld

    override val currentShipClientWorld: ShipWorld
        get() = Minecraft.getInstance().shipObjectWorld!!
}
