package org.valkyrienskies.mod.common.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

class VSPhysicsEntity(type: EntityType<VSPhysicsEntity>, level: Level) : Entity(type, level) {
    override fun defineSynchedData() {}

    override fun readAdditionalSaveData(compoundTag: CompoundTag) {}

    override fun addAdditionalSaveData(compoundTag: CompoundTag) {}

    override fun getAddEntityPacket(): Packet<*> {
        return ClientboundAddEntityPacket(this)
    }
}
