package org.valkyrienskies.mod.compat.flywheel

import dev.engine_room.flywheel.api.visualization.VisualizationManager
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Matrix4f
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.compat.LoadedMods
import org.valkyrienskies.mod.compat.LoadedMods.FlywheelVersion

object FlywheelCompat {

    fun initClient() {
        // if (LoadedMods.flywheel != FlywheelVersion.V1) return
        //
        // vsCore.shipLoadEventClient.on { e ->
        //     VisualizationHelper.queueAdd(ShipEffect(e.ship, Minecraft.getInstance().level!!))
        // }
        //
        // vsCore.shipUnloadEventClient.on { e ->
        //     VisualizationHelper.queueRemove(ShipEffect.getShipEffect(e.ship))
        // }
    }

    fun validate(blockEntity: BlockEntity, level: Level): Boolean {
        if (!VisualizationHelper.canVisualize(blockEntity)) return false
        if (VisualizationManager.get(level) == null) return false
        return true
    }

    private fun getEffect(blockEntity: BlockEntity): ShipEffect? {
        if (LoadedMods.flywheel != FlywheelVersion.V1) return null
        if (blockEntity.level?.isClientSide != true) return null
        if (!VisualizationManager.supportsVisualization(blockEntity.level)) return null
        if (!VisualizationHelper.canVisualize(blockEntity)) return null

        val ship = blockEntity.level!!.getShipManagingPos(blockEntity.blockPos) ?: return null
        return ShipEffect.getShipEffect(ship as ClientShip)
    }

    fun setShipEffectDirty(ship: ClientShip, x: Int, y: Int, z: Int, important: Boolean = false) {
        ShipEffect.Companion.getShipEffect(ship).setDirty(x, y, z, important)
    }

    fun onLightUpdate(ship: ClientShip, pos: SectionPos) {
        var manager = (ShipEffect.getShipEffect(ship).manager) as VisualManagerImpl<*, *>?
        manager?.onLightUpdate(pos.asLong())
    }

    lateinit var viewProjection: Matrix4f
}
