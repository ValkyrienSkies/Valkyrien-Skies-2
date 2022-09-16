package org.valkyrienskies.mod.client.audio

import net.minecraft.client.resources.sounds.TickableSoundInstance
import org.joml.Vector3dc

interface VelocityTickableSoundInstance : TickableSoundInstance {

    val velocity: Vector3dc
}
