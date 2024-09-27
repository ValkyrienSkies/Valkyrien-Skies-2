package org.valkyrienskies.mod.compat

import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.GameTickForceApplier
import org.valkyrienskies.mod.common.util.toMinecraft
import weather2.ServerTickHandler
import weather2.weathersystem.storm.StormObject

object Weather2Compat {
    fun tick(level: ServerLevel) {
        val mgr = ServerTickHandler
            .getWeatherManagerFor(level.dimension())

        val windMult = VSGameConfig.SERVER.Weather2.windMultiplier
        val windMax = VSGameConfig.SERVER.Weather2.windMaxVel
        val stormDampen = 1.0f - VSGameConfig.SERVER.Weather2.stormDampening
        val stormRange = VSGameConfig.SERVER.Weather2.stormRange

        val vec = Vector3d()
        level.shipObjectWorld.loadedShips.forEach { ship ->
            val forces = ship.getAttachment<GameTickForceApplier>()!!

            val com = ship.inertiaData.centerOfMassInShip

            ship.shipToWorld.transformPosition(com, vec)
            val pos = vec.toMinecraft()

            val motion = ship.velocity.toMinecraft()

            val mass = ship.inertiaData.mass

            var forcePlusMotion = mgr.windManager.applyWindForceImpl(
                pos,
                motion,
                mass.toFloat(),
                windMult,
                windMax,
                true,
            )

            fun applyForcePlusMotion() {
                vec.x = forcePlusMotion.x
                vec.y = forcePlusMotion.y
                vec.z = forcePlusMotion.z

                vec.sub(ship.velocity)
                vec.mul(mass)

                forces.applyInvariantForceToPos(vec, com)
            }

            applyForcePlusMotion()

            mgr.getStormsAround(pos, stormRange).forEach {
                if (it is StormObject) {
                    runCatching { // prevent Cannot read field "listLayers" because "this.tornadoFunnelSimple" is null    at weather2.weathersystem.storm.StormObject.spinObject(StormObject.java:2503)
                        forcePlusMotion = it.spinObject(
                            pos,
                            forcePlusMotion,
                            false,
                            stormDampen,
                            stormDampen,
                            false,
                            0.0f,
                        )

                        applyForcePlusMotion()
                    }
                }
            }
        }
    }
}
