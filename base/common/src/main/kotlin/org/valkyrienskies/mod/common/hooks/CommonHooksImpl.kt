package org.valkyrienskies.mod.common.hooks

import net.minecraft.client.Minecraft
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.apigame.hooks.CoreHooksOut
import org.valkyrienskies.core.apigame.hooks.PlayState
import org.valkyrienskies.core.apigame.hooks.PlayState.CLIENT_MULTIPLAYER
import org.valkyrienskies.core.apigame.hooks.PlayState.CLIENT_SINGLEPLAYER
import org.valkyrienskies.core.apigame.hooks.PlayState.CLIENT_TITLESCREEN
import org.valkyrienskies.core.apigame.hooks.PlayState.SERVERSIDE
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.shipObjectWorld

abstract class CommonHooksImpl : CoreHooksOut {

    override val playState: PlayState
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
