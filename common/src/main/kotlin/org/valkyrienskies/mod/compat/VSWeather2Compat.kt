package org.valkyrienskies.mod.compat

import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ShipForcesInducer
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.api.ships.saveAttachment
import org.valkyrienskies.mod.common.shipObjectWorld

object VSWeather2Compat {
    fun tick(level: ServerLevel) {
        if (!LoadedMods.weather2)
            return

        val mgr = ServerTickHandler.getWeatherManagerFor(level)

        level.shipObjectWorld.loadedShips.forEach { ship ->
            WindForces
                .getOrCreate(ship)
                .update(ship, mgr)
        }
    }

    private class WindForces : ShipForcesInducer {

        @Volatile
        var forceX = 0.0

        @Volatile
        var forceY = 0.0

        @Volatile
        var forceZ = 0.0

        fun update(ship: ServerShip, mgr: WeatherManagerServer) {
            val pos = ship.transform.shipToWorld
                .transformPosition(Vector3d(ship.inertiaData.centerOfMassInShip))

            val wind = mgr.getWindForce(pos)
            forceX = wind.x.toDouble()
            forceY = wind.y.toDouble()
            forceZ = wind.z.toDouble()
        }

        private val physTickVec = Vector3d()
        override fun applyForces(physShip: PhysShip) {
            physTickVec.x = forceX
            physTickVec.y = forceY
            physTickVec.z = forceZ
            physShip.applyInvariantForce(physTickVec)
        }

        companion object {

            fun getOrCreate(ship: ServerShip) =
                ship.getAttachment<WindForces>()
                    ?: WindForces().also(ship::saveAttachment)

        }

    }
}
