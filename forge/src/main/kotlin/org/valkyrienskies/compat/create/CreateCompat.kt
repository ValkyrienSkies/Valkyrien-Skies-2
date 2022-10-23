package org.valkyrienskies.compat.create

import com.jozufozu.flywheel.backend.instancing.IInstance
import com.jozufozu.flywheel.event.BeginFrameEvent
import com.jozufozu.flywheel.event.RenderLayerEvent
import com.simibubi.create.AllMovementBehaviours
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.MinecraftForge.EVENT_BUS
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.core.api.Ship

object CreateCompat {
    private val flwShips = mutableMapOf<Ship, FlwShip>()

    fun init() {
        EVENT_BUS.addListener(::onBeginFrame)
        EVENT_BUS.addListener(::onRenderLayer)
    }

    private fun onBeginFrame(event: BeginFrameEvent) {
        flwShips.forEach { (_, ship) -> ship.beginFrame(event) }
    }

    private fun onRenderLayer(event: RenderLayerEvent) {
        flwShips.forEach { (_, ship) ->
            ship.setupMatrices(event.stack, event.camX, event.camY, event.camZ)
            ship.renderInstances(event)
        }
    }

    fun addBlockEntityToShip(level: Level, ship: Ship, be: BlockEntity) {
        AllMovementBehaviours.of(be.blockState)?.let {
            val flwShip = flwShips.getOrPut(ship) { FlwShip(ship as ClientShip, level) }
            return flwShip.addInstancedTile(be)
        }
    }

    fun removeBlockEntityFromShip(Level: Level, ship: Ship, be: BlockEntity) {
        flwShips[ship]?.removeInstancedTile(be)
    }

    fun getIInstance(ship: Ship, be: BlockEntity): IInstance? =
        flwShips[ship]?.getIInstance(be)
}
