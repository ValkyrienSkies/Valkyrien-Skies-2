package org.valkyrienskies.mod.common.util

import com.fasterxml.jackson.annotation.JsonIgnore
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.mod.common.config.VSGameConfig

//todo: maybe make this more complex in the future, but the beyond oxygen method is more than acceptable for now
class BuoyancyHandlerAttachment : ShipPhysicsListener {
    val buoyancyData = BuoyancyData()

    override fun physTick(
        physShip: PhysShip, physLevel: PhysLevel
    ) {
        if (!VSGameConfig.SERVER.enablePocketBuoyancy) return
        if (buoyancyData.pocketCenterAverage.lengthSquared() == 0.0) return // No pockets
        physShip.buoyantFactor = 1.0 + (buoyancyData.pocketVolumeTotal * VSGameConfig.SERVER.buoyancyFactorPerPocketVolume)
        return
        val coverage = physShip.liquidOverlap
        if (coverage <= 0.0) {
            return
        }
        val upwardForce = coverage * buoyancyData.pocketVolumeTotal * VSGameConfig.SERVER.buoyancyFactorPerPocketVolume
        physShip.applyWorldForceToModelPos(
            Vector3d(0.0, upwardForce, 0.0),
            buoyancyData.pocketCenterAverage
            //buoyancyData.pocketCenterAverage
        )
    }


    data class BuoyancyData(
        @Volatile
        var pocketVolumeTotal: Double = 0.0,

        @Volatile
        @JsonIgnore
        var pocketCenterAverage: Vector3dc = Vector3d()
    )
}
