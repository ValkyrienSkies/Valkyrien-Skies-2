package org.valkyrienskies.mod.fabric.client

import net.fabricmc.api.ClientModInitializer
import org.valkyrienskies.mod.fabric.common.VSFabricNetworking

/**
 * This class only runs on the client, used to initialize client only code. See [ClientModInitializer] for more details.
 */
class ValkyrienSkiesModFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        VSFabricNetworking.registerClientPacketHandlers()
    }
}
