package org.valkyrienskies.mod.fabric

import org.valkyrienskies.mod.ValkyrienSkiesMod
import org.valkyrienskies.mod.VSNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.util.Identifier

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