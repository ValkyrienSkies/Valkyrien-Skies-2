package org.valkyrienskies.mod.fabric.client

import net.fabricmc.api.ClientModInitializer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

class ValkyrienSkiesModFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Put anything initialized on fabric-side client here.
        ValkyrienSkiesMod.initClient()
    }
}
