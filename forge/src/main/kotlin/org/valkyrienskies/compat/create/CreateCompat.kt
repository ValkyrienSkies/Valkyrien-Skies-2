package org.valkyrienskies.compat.create

import com.jozufozu.flywheel.event.BeginFrameEvent
import com.jozufozu.flywheel.event.RenderLayerEvent
import com.simibubi.create.AllMovementBehaviours
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.MinecraftForge.EVENT_BUS
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.mod.common.hooks.VSEvents

object CreateCompat {
    val flwShips = mutableMapOf<Ship, FlwShip>()
    val nextFrame = mutableListOf<Runnable>()

    fun init() {
        VSEvents.ShipBlockChangeEvent.on { it -> onBlockPlace(it.level, it.ship, it.pos, it.newState) }
        EVENT_BUS.addListener(::onBeginFrame)
        EVENT_BUS.addListener(::onRenderLayer)
    }

    private fun onBeginFrame(event: BeginFrameEvent) {
        flwShips.forEach { (_, ship) -> ship.beginFrame(event) }
        nextFrame.forEach(Runnable::run)
        nextFrame.clear()
    }

    private fun onRenderLayer(event: RenderLayerEvent) {
        flwShips.forEach { (_, ship) ->
            ship.setupMatrices(event.stack, event.camX, event.camY, event.camZ)
            ship.renderInstances(event)
        }
    }

    private fun onBlockPlace(level: Level, ship: Ship, pos: BlockPos, state: BlockState) {
        if (!level.isClientSide) return

        AllMovementBehaviours.of(state)?.let {
            val flwShip = flwShips.getOrPut(ship) { FlwShip(ship as ClientShip, level) }
            val be = level.getBlockEntity(pos) ?: throw IllegalStateException(
                "BlockEntity is null when it has a movement behaviour"
            )
            flwShip.addInstancedTile(be)
            //nextFrame.add { InstancedRenderDispatcher.getTiles(level).remove(be) }
        }
    }
}
