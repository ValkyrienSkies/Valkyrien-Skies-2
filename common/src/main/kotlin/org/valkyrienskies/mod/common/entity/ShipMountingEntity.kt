package org.valkyrienskies.mod.common.entity

import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.setAttachment
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.isBlockInShipyard
import org.valkyrienskies.mod.common.networking.PacketPlayerDriving
import org.valkyrienskies.mod.common.vsCore

open class ShipMountingEntity(type: EntityType<ShipMountingEntity>, level: Level) : Entity(type, level) {
    // Decides if this entity controls the ship it is in.
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
        if (!level().isClientSide && passengers.isEmpty()) {
            // Kill this entity if nothing is riding it
            kill()
            return
        }

        if (level().getShipObjectManagingPos(blockPosition()) != null)
            sendDrivingPacket()
    }

    // This is a partial fix for mounting ships that have been deleted
    // TODO: Make a full fix eventually
    /*
    override fun positionRider(entity: Entity) {
        if (level().isBlockInShipyard(position()) && level().getShipManagingPos(position()) == null) {
            // Stop rider positioning if we can't find the ship
            entity.removeVehicle()
            return
        }
        super.positionRider(entity)
    }

     */

    // This is a partial fix for mounting ships that have been deleted
    // TODO: Make a full fix eventually
    override fun getDismountLocationForPassenger(livingEntity: LivingEntity): Vec3 {
        if (level().isBlockInShipyard(position()) && level().getShipManagingPos(position()) == null) {
            // Don't teleport to the ship if we can't find the ship
            return livingEntity.position()
        }
        return super.getDismountLocationForPassenger(livingEntity)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {}

    override fun addAdditionalSaveData(compound: CompoundTag) {}

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

    override fun remove(removalReason: RemovalReason) {
        if (this.isController && !level().isClientSide)
            (level().getShipObjectManagingPos(blockPosition()) as LoadedServerShip?)
                ?.setAttachment<SeatedControllingPlayer>(null)
        super.remove(removalReason)
    }

    private fun sendDrivingPacket() {
        if (!level().isClientSide) return
        // todo: custom keybinds for going up down and all around but for now lets just use the mc defaults
        val opts = Minecraft.getInstance().options
        val forward = opts.keyUp.isDown
        val backward = opts.keyDown.isDown
        val left = opts.keyLeft.isDown
        val right = opts.keyRight.isDown
        val up = opts.keyJump.isDown
        val sprint = this.controllingPassenger?.isSprinting == true
        val down = VSKeyBindings.shipDown.get().isDown
        val cruise = VSKeyBindings.shipCruise.get().isDown

        val impulse = Vector3f()
        impulse.z = if (forward == backward) 0.0f else if (forward) 1.0f else -1.0f
        impulse.x = if (left == right) 0.0f else if (left) 1.0f else -1.0f
        impulse.y = if (up == down) 0.0f else if (up) 1.0f else -1.0f

        with(vsCore.simplePacketNetworking) {
            PacketPlayerDriving(impulse, sprint, cruise).sendToServer()
        }
    }

    override fun getControllingPassenger(): LivingEntity? {
        return if (isController) {
            this.passengers.getOrNull(0) as? LivingEntity
        } else {
            null
        }
    }

    override fun getAddEntityPacket(serverEntity: ServerEntity): Packet<ClientGamePacketListener> {
        return ClientboundAddEntityPacket(this, serverEntity)
    }
}
