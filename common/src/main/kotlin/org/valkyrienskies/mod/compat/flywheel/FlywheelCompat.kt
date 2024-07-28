package org.valkyrienskies.mod.compat.flywheel

import dev.engine_room.flywheel.api.visualization.VisualizationManager
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper
import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Matrix4f
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.impl.hooks.VSEvents
import org.valkyrienskies.mod.common.getShipManagingPos

object FlywheelCompat {
    val isFlywheelInstalled: Boolean by lazy {
        try {
            Class.forName("dev.engine_room.flywheel.api.Flywheel")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    fun initClient() {
        if (!isFlywheelInstalled) return

        VSEvents.shipLoadEventClient.on { e ->
            VisualizationHelper.queueAdd(ShipEffect(e.ship, Minecraft.getInstance().level!!))
        }

        VSEvents.shipUnloadEventClient.on { e ->
            VisualizationHelper.queueRemove(ShipEffect.getShipEffect(e.ship))
        }
    }

    private fun getEffect(blockEntity: BlockEntity): ShipEffect? {
        if (!isFlywheelInstalled) return null
        if (blockEntity.level?.isClientSide != true) return null
        if (!VisualizationManager.supportsVisualization(blockEntity.level)) return null
        if (!VisualizationHelper.canVisualize(blockEntity)) return null

        val ship = blockEntity.level!!.getShipManagingPos(blockEntity.blockPos) ?: return null
        return ShipEffect.getShipEffect(ship as ClientShip)
    }

    fun addBlockEntity(blockEntity: BlockEntity) {
        getEffect(blockEntity)?.queueAddition(blockEntity)
    }

    fun removeBlockEntity(blockEntity: BlockEntity) {
        getEffect(blockEntity)?.queueRemoval(blockEntity)
    }

    fun updateBlockEntity(blockEntity: BlockEntity) {
        getEffect(blockEntity)?.queueUpdate(blockEntity)
    }

    lateinit var viewProjection: Matrix4f
}
