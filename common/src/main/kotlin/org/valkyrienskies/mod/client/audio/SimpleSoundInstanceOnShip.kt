package org.valkyrienskies.mod.client.audio

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.Ship

class SimpleSoundInstanceOnShip : SimpleSoundInstance, VelocityTickableSoundInstance {

    private val ship: Ship

    constructor(
        resourceLocation: ResourceLocation,
        soundSource: SoundSource,
        volume: Float,
        pitch: Float,
        random: RandomSource,
        looping: Boolean,
        delay: Int,
        attenuation: Attenuation,
        x: Double,
        y: Double,
        z: Double,
        relative: Boolean,
        ship: Ship
    ) : super(resourceLocation, soundSource, volume, pitch, random, looping, delay, attenuation, x, y, z, relative) {
        this.ship = ship
    }

    constructor(
        soundEvent: SoundEvent, soundSource: SoundSource, volume: Float, pitch: Float, random: RandomSource,
        looping: Boolean, delay: Int,
        attenuation: Attenuation, x: Double, y: Double, z: Double, ship: Ship
    ) : this(soundEvent.location, soundSource, volume, pitch, random, looping, delay, attenuation, x, y, z, false, ship)

    constructor(
        soundEvent: SoundEvent, soundSource: SoundSource, volume: Float, pitch: Float, random: RandomSource, x: Double,
        y: Double, z: Double,
        ship: Ship
    ) : this(soundEvent, soundSource, volume, pitch, random, false, 0, LINEAR, x, y, z, ship)

    private val originalPos = Vector3d(x, y, z)

    override val velocity: Vector3dc
        get() = ship.velocity

    override fun isStopped(): Boolean = false

    override fun tick() {
        val newPos = ship.shipToWorld.transformPosition(originalPos, Vector3d())
        this.x = newPos.x
        this.y = newPos.y
        this.z = newPos.z
    }
}
