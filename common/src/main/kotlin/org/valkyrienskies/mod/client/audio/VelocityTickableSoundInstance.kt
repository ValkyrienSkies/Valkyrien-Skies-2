package org.valkyrienskies.mod.client.audio

import net.minecraft.client.resources.sounds.TickableSoundInstance
import org.joml.Vector3dc

interface VelocityTickableSoundInstance : TickableSoundInstance {

    val velocity: Vector3dc

    /**
     * updateVelocity will be called every game tick (about 20 times per second).
     * Sound instances can update the sound source {@link #velocity} here.
     */
    fun updateVelocity() {}
}
