package org.valkyrienskies.mod.common.entity

import net.minecraft.world.entity.Entity

interface ShipMountedToDataProvider {
    fun provideShipMountedToData(passenger: Entity): ShipMountedToData?
}
