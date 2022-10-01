package org.valkyrienskies.mod.common.entity

import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import org.joml.Vector3f
import org.valkyrienskies.core.api.setAttachment
import org.valkyrienskies.core.game.ships.ShipObjectServer
import org.valkyrienskies.core.networking.simple.sendToServer
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.networking.PacketPlayerDriving

class ShipMountingEntity(type: EntityType<ShipMountingEntity>, level: Level) : Entity(type, level) {

    // Decides if this entity controlls the ship it is in.
    // Only needs to be set serverside
    var isController = false

    init {
        // Don't prevent blocks colliding with this entity from being placed
        blocksBuilding = false
        // Don't collide with terrain
        noPhysics = true
    }

    override fun tick() {
        super.tick()
        if (!level.isClientSide && passengers.isEmpty()) {
            // Kill this entity if nothing is riding it
            kill()
            return
        }

        if (level.getShipObjectManagingPos(blockPosition()!!) != null)
            sendDrivingPacket()
    }

    override fun readAdditionalSaveData(compound: CompoundTag?) {
    }

    override fun addAdditionalSaveData(compound: CompoundTag?) {
    }

    override fun defineSynchedData() {
    }

    override fun remove() {
        if (this.isController && !level.isClientSide)
            (level.getShipObjectManagingPos(blockPosition()!!) as ShipObjectServer?)
                ?.setAttachment<SeatedControllingPlayer>(null)
        super.remove()
    }

    private fun sendDrivingPacket() {
        if (!level.isClientSide) return
        // todo: custom keybinds for going up down and all around but for now lets just use the mc defaults
        val opts = Minecraft.getInstance().options
        val forward = opts.keyUp.isDown
        val backward = opts.keyDown.isDown
        val left = opts.keyLeft.isDown
        val right = opts.keyRight.isDown
        val up = opts.keyJump.isDown
        val down = VSKeyBindings.shipDown.get().isDown

        val impulse = Vector3f()
        impulse.z = if (forward == backward) 0.0f else if (forward) 1.0f else -1.0f
        impulse.x = if (left == right) 0.0f else if (left) 1.0f else -1.0f
        impulse.y = if (up == down) 0.0f else if (up) 1.0f else -1.0f

        PacketPlayerDriving(impulse).sendToServer()
    }

    override fun getControllingPassenger(): Entity? {
        return if (isController) this.passengers.getOrNull(0) else null
    }

    override fun getAddEntityPacket(): Packet<*> {
        return ClientboundAddEntityPacket(this)
    }
}
