package org.valkyrienskies.mod.common.networking

import net.minecraft.world.InteractionHand
import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketGravitronFreeze(val hand: InteractionHand = InteractionHand.MAIN_HAND) : SimplePacket
