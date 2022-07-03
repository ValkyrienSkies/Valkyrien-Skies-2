package org.valkyrienskies.mod.forge.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.fml.network.NetworkRegistry
import net.minecraftforge.fml.network.PacketDistributor
import net.minecraftforge.fml.network.simple.SimpleChannel
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer

object VSForgeNetworking {

    private const val protocolVersion = "1"
    val vsForgeChannel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet"),
        { protocolVersion },
        { anObject: String? -> protocolVersion == anObject },
        { anObject: String? -> protocolVersion == anObject }
    )

    internal fun registerForgeNetworking() {
        registerClientPacketHandlers()
        injectForgePacketSenders()
    }

    private fun registerClientPacketHandlers() {
        // This gibberish is brought to you by forge
        @Suppress("INACCESSIBLE_TYPE")
        vsForgeChannel.registerMessage(
            0,
            MessageVSPacket::class.java,
            { _, _ -> },
            { packetBuffer: FriendlyByteBuf -> MessageVSPacket(packetBuffer) },
            { vsPacket, contextSupplier ->
                contextSupplier.get().enqueueWork {
                    VSNetworking.TCP.onReceiveClient(vsPacket.buf)
                }
                contextSupplier.get().packetHandled = true
            }
        )
    }

    private fun injectForgePacketSenders() {
        VSNetworking.TCP.rawSendToClient = { data, player ->
            vsForgeChannel.send(
                PacketDistributor.PLAYER.with { player.mcPlayer as ServerPlayer },
                MessageVSPacket(data)
            )
        }
    }
}
