package org.valkyrienskies.mod.forge.common

import io.netty.buffer.ByteBuf
import net.minecraftforge.event.network.CustomPayloadEvent
import net.minecraftforge.network.simple.handler.SimplePacket
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.vsCore
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck

/**
 * A wrapper of [IVSPacket] used to register forge networking.
 */
class MessageVSPacket(val buf: ByteBuf): SimplePacket<MessageVSPacket> {
    override fun handle(
        `object`: MessageVSPacket?,
        context: CustomPayloadEvent.Context,
    ): Boolean {
        if (context.isClientSide) {
            vsCore.hooks.onReceiveClient(buf)
            return true
        } else {
            if (context.sender != null) {
                vsCore.hooks.onReceiveServer(buf, (context.sender as PlayerDuck).vs_getPlayer())
                return true
            } else {
                println("context.sender was null?")
                return false
            }
        }
    }
}
