package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.gameevent.GameEvent
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.mixin.feature.ship_interactions.ButtonAccessor
import java.util.WeakHashMap

object ShipButtons {
    private val held = WeakHashMap<ServerLevel, Map<BlockPos, ButtonBlock>>()

    fun isPushed(level: ServerLevel, pos: BlockPos, state: BlockState, source: Ship, target: Ship?): Boolean {
        // Use the full button shape so a pressed button keeps the same contact area.
        val shape = state.setValue(ButtonBlock.POWERED, false).getShape(level, pos)
        if (shape.isEmpty) return false
        val inward = when (state.getValue(BlockStateProperties.ATTACH_FACE)) {
            AttachFace.FLOOR -> Vector3d(0.0, -1.0, 0.0)
            AttachFace.CEILING -> Vector3d(0.0, 1.0, 0.0)
            else -> state.getValue(BlockStateProperties.HORIZONTAL_FACING).normal.toJOMLD().negate()
        }
        return ShipBlockContacts.find(level, source, shape.bounds().move(pos).toJOML(), target,
            sweep = false, pushNormal = inward) != null
    }

    fun update(level: ServerLevel, buttons: Map<BlockPos, ButtonBlock>) {
        val previous = held.put(level, HashMap(buttons)) ?: emptyMap()
        for ((pos, block) in buttons) {
            if (!level.hasChunkAt(pos)) continue
            val state = level.getBlockState(pos)
            if (state.block !== block || state.getValue(ButtonBlock.POWERED)) continue
            block.press(state, level, pos)
            (block as ButtonAccessor).`vs$playSound`(null, level, pos, true)
            level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, pos)
        }
        for ((pos, block) in previous) {
            if (pos in buttons || !level.hasChunkAt(pos)) continue
            val state = level.getBlockState(pos)
            if (state.block === block && state.getValue(ButtonBlock.POWERED)) {
                // Let an arrow keep a wooden button pressed after the ship leaves.
                (block as ButtonAccessor).`vs$checkPressed`(state, level, pos)
            }
        }
    }

    @JvmStatic
    fun isHeld(level: Level, pos: BlockPos): Boolean = level is ServerLevel && held[level]?.containsKey(pos) == true

    fun clear() = held.clear()
}
