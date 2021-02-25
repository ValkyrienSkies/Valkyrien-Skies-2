package org.valkyrienskies.mod.forge.common

import org.valkyrienskies.core.networking.IVSPacket

/**
 * A wrapper of [IVSPacket] used to register forge networking.
 */
class MessageVSPacket(val vsPacket: IVSPacket)