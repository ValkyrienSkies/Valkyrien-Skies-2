package net.examplemod.fabric

import net.examplemod.ExampleMod
import net.examplemod.VSNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.resources.ResourceLocation

/**
 * Registers VS with the Fabric networking API.
 */
object VSFabricNetworking {
    internal val VS_PACKET_ID = ResourceLocation(ExampleMod.MOD_ID, "vs_packet")

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