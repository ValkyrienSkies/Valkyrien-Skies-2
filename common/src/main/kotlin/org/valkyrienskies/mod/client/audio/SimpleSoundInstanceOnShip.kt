package org.valkyrienskies.mod.client.audio

import net.minecraft.client.Minecraft
import org.valkyrienskies.mod.common.ShipEffectLocations
import org.valkyrienskies.mod.common.allShips
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import org.valkyrienskies.core.api.ships.Ship
import org.joml.Vector3d
import org.joml.Vector3dc
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR

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
        val newPos = worldPosition()
        this.x = newPos.x
        this.y = newPos.y
        this.z = newPos.z
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

    private fun worldPosition(): Vector3d {
        val mc = Minecraft.getInstance()
        val pieces = mc.level?.allShips?.getSplitPieces(ship.id)
        if (!pieces.isNullOrEmpty()) {
            val eye = mc.gameRenderer.mainCamera.position
            ShipEffectLocations.nearestBlock(pieces, originalPos.x, originalPos.y, originalPos.z, eye)?.let { return it }
        }
        return ship.shipToWorld.transformPosition(originalPos, Vector3d())
    }

    override var velocity: Vector3dc = Vector3d()
        private set

    override fun isStopped(): Boolean = false

    override fun tick() {
        val newPos = worldPosition()
        this.velocity = newPos.sub(this.x, this.y, this.z, Vector3d())
        this.x = newPos.x
        this.y = newPos.y
        this.z = newPos.z
    }
}
