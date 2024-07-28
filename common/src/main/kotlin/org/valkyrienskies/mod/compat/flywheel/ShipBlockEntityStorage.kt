package org.valkyrienskies.mod.compat.flywheel

import dev.engine_room.flywheel.api.visual.BlockEntityVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.impl.visualization.storage.Storage
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

//TODO uses impl prone to change will prob break
class ShipBlockEntityStorage(ctx: VisualizationContext) : Storage<BlockEntity>(ctx) {

    override fun createRaw(obj: BlockEntity, partialTick: Float): BlockEntityVisual<*>? {
        val visualizer = VisualizationHelper.getVisualizer(obj)
            ?: return null

        val visual = visualizer.createVisual(visualizationContext, obj, partialTick)
        return visual
    }

    override fun willAccept(blockEntity: BlockEntity): Boolean {
        if (blockEntity.isRemoved)  return false
        if (!VisualizationHelper.canVisualize(blockEntity)) return false

        val level: Level = blockEntity.level ?: return false
        if (level.isEmptyBlock(blockEntity.blockPos)) return false

        val pos: BlockPos = blockEntity.blockPos
        val existingChunk = level.getChunkForCollisions(pos.x shr 4, pos.z shr 4)

        return existingChunk != null
    }
}
