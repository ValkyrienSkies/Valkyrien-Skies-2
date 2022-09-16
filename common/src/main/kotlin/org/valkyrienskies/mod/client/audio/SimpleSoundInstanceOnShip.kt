package org.valkyrienskies.mod.client.audio

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.Ship

class SimpleSoundInstanceOnShip : SimpleSoundInstance, VelocityTickableSoundInstance {

    private val ship: Ship

    constructor(
        resourceLocation: ResourceLocation,
        soundSource: SoundSource,
        volume: Float,
        pitch: Float,
        looping: Boolean,
        delay: Int,
        attenuation: Attenuation,
        x: Double,
        y: Double,
        z: Double,
        relative: Boolean,
        ship: Ship
    ) : super(resourceLocation, soundSource, volume, pitch, looping, delay, attenuation, x, y, z, relative) {
        this.ship = ship
    }

    constructor(
        soundEvent: SoundEvent, soundSource: SoundSource, f: Float, g: Float, bl: Boolean, i: Int,
        attenuation: Attenuation, d: Double, e: Double, h: Double, ship: Ship
    ) : this(soundEvent.location, soundSource, f, g, bl, i, attenuation, d, e, h, false, ship)

    constructor(
        soundEvent: SoundEvent, soundSource: SoundSource, f: Float, g: Float, d: Double, e: Double, h: Double,
        ship: Ship
    ) : this(soundEvent, soundSource, f, g, false, 0, LINEAR, d, e, h, ship)

    private val originalPos = Vector3d(x, y, z)

    override val velocity: Vector3dc
        get() = ship.velocity

    override fun isStopped(): Boolean = false

    override fun tick() {
        val newPos = ship.shipTransform.shipToWorldMatrix.transformPosition(originalPos, Vector3d())
        this.x = newPos.x
        this.y = newPos.y
        this.z = newPos.z
    }
}
