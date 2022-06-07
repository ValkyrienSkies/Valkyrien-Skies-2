package org.valkyrienskies.mod.fabric.common

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.resources.ResourceLocation
import org.valkyrienskies.mod.common.VSNetworking
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

/**
 * Registers VS with the Fabric networking API.
 */
object VSFabricNetworking {
    internal val VS_PACKET_ID = ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet")

    /**
     * Only run on client
     */
    internal fun registerClientPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_ID, VSClientPlayChannelHandler)
    }

    internal fun injectFabricPacketSenders() {
        VSNetworking.shipDataPacketToClientSender = VSFabricServerToClientPacketSender
    }
}
