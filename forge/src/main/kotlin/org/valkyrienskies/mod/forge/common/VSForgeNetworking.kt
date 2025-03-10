package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.ChannelBuilder
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.SimpleChannel
import org.valkyrienskies.core.apigame.hooks.CoreHooksIn
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer

object VSForgeNetworking {

    private val vsForgeChannel: SimpleChannel = ChannelBuilder.named(
        ResourceLocation.fromNamespaceAndPath(ValkyrienSkiesMod.MOD_ID, "vs_packet")
    ).simpleChannel()

    fun registerPacketHandlers(hooks: CoreHooksIn) {
        // Loading this class registers the handlers
        println("Class loaded!")
    }

    fun sendToClient(data: ByteBuf, player: IPlayer) {
        vsForgeChannel.send(
            MessageVSPacket(data),
            PacketDistributor.PLAYER.with(player.mcPlayer as ServerPlayer)
        )
    }

    fun sendToServer(data: ByteBuf) {
        vsForgeChannel.send(MessageVSPacket(data), PacketDistributor.SERVER.noArg())
    }
}
