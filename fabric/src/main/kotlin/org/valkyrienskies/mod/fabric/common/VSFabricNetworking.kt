package org.valkyrienskies.mod.fabric.common

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.util.Identifier
import org.valkyrienskies.mod.common.VSNetworking
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

/**
 * Registers VS with the Fabric networking API.
 */
object VSFabricNetworking {
    internal val VS_PACKET_ID = Identifier(ValkyrienSkiesMod.MOD_ID, "vs_packet")

    internal fun registerFabricNetworking() {
        registerClientPacketHandlers()
        injectFabricPacketSenders()
    }

    private fun registerClientPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_ID, VSClientPlayChannelHandler)
    }

    private fun injectFabricPacketSenders() {
        VSNetworking.shipDataPacketToClientSender = VSFabricServerToClientPacketSender
    }
}
