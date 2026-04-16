package org.valkyrienskies.mod.compat

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toMinecraft
import weather2.ServerTickHandler
import weather2.weathersystem.storm.StormObject

object Weather2Compat {
    fun tick(level: ServerLevel) {
        if (!VSGameConfig.SERVER.Weather2.enableWeatherCompat) return

        val mgr = ServerTickHandler
            .getWeatherManagerFor(level.dimension())

        // Devided by 1000 because we need a very small multiplier but the config is weird with very small decimals
        val windMult = VSGameConfig.SERVER.Weather2.windMultiplier / 1000
        val windMax = VSGameConfig.SERVER.Weather2.windMaxVel
        val stormDampen = 1.0f - VSGameConfig.SERVER.Weather2.stormDampening
        val stormRange = VSGameConfig.SERVER.Weather2.stormRange


        level.shipObjectWorld.loadedShips.forEach { ship ->
            val vec = Vector3d()
            val forces = ValkyrienSkiesMod.getOrCreateGTPA(ship.chunkClaimDimension)

            val com = ship.inertiaData.centerOfMassInShip

            val pos = vec.toMinecraft()

            ship.shipToWorld.transformPosition(com, vec)
            ship.dragController?.setWindDirection(Vector3d(0.0, 0.0, -1.0).rotateY(Math.toRadians(
                mgr.windManager.getWindAngle(pos).toDouble()
            )), "WEATHER2")
            ship.dragController?.setWindSpeed(
                mgr.windManager.getWindSpeed(
                    BlockPos.containing(pos)
                ).toDouble(),
                "WEATHER2"
            )

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

                forces.applyWorldForce(ship.id, vec, null)
            }

            //applyForcePlusMotion()

            mgr.getStormsAround(pos, stormRange).forEach {
                if (it is StormObject && it.tornadoFunnelSimple != null) {
                    forcePlusMotion = it.spinObject(
                        pos,
                        forcePlusMotion,
                        false,
                        stormDampen,
                        stormDampen,
                        true,
                        0.0f,
                    )

                    applyForcePlusMotion()
                }
            }
        }
    }
}
