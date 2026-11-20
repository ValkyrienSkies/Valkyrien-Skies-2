package org.valkyrienskies.mod.api.portals

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.impl.api_impl.config.ConfigPhysicsBackendType
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.portals.ShipPortalRegistry
import kotlin.math.abs

data class ShipPortalMouth(val center: Vec3, val right: Vec3, val up: Vec3) {
    init {
        require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite())
        require(validAxes(right, up)) { "Portal axes must be perpendicular unit vectors" }
    }

    internal companion object {
        fun validAxes(right: Vec3, up: Vec3) = abs(right.lengthSqr() - 1.0) <= 1e-5 &&
            abs(up.lengthSqr() - 1.0) <= 1e-5 && abs(right.dot(up)) <= 1e-5
    }
}

data class ShipPortalPair(val a: ShipPortalMouth, val b: ShipPortalMouth, val halfWidth: Double, val halfHeight: Double) {
    init {
        require(halfWidth.isFinite() && halfWidth > 0.0 && halfHeight.isFinite() && halfHeight > 0.0)
    }
}

object ShipPortals {
    @JvmStatic
    fun isSupported(): Boolean = VSCoreConfig.SERVER.physics.physicsBackend == ConfigPhysicsBackendType.KRUNCH_KONSTANT

    @JvmStatic
    fun set(level: ServerLevel, key: ResourceLocation, pair: ShipPortalPair) {
        set(level, key, pair, null, null)
    }

    @JvmStatic
    fun set(level: ServerLevel, key: ResourceLocation, pair: ShipPortalPair, carrierA: Long?, carrierB: Long?) {
        if (isSupported()) ShipPortalRegistry.updates.set(level.dimensionId, key, pair, carrierA, carrierB)
    }

    @JvmStatic
    fun remove(level: ServerLevel, key: ResourceLocation) {
        if (isSupported()) ShipPortalRegistry.updates.remove(level.dimensionId, key)
    }
}
