package org.valkyrienskies.mod.common.hooks

import net.minecraft.client.Minecraft
import org.valkyrienskies.core.game.ships.ShipObjectClientWorld
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld
import org.valkyrienskies.core.hooks.AbstractCoreHooks
import org.valkyrienskies.core.hooks.PlayState
import org.valkyrienskies.core.hooks.PlayState.CLIENT_MULTIPLAYER
import org.valkyrienskies.core.hooks.PlayState.CLIENT_SINGLEPLAYER
import org.valkyrienskies.core.hooks.PlayState.CLIENT_TITLESCREEN
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.shipObjectWorld

abstract class CommonHooksImpl : AbstractCoreHooks() {

    override val playState: PlayState
        get() {
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

    override val currentShipServerWorld: ShipObjectServerWorld?
        get() = ValkyrienSkiesMod.currentServer?.shipObjectWorld

    override val currentShipClientWorld: ShipObjectClientWorld
        get() = Minecraft.getInstance().shipObjectWorld
}
